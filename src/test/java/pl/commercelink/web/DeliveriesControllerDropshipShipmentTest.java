package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryReceptionService;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.inventory.deliveries.DropshipDeliveryCompletion;
import pl.commercelink.inventory.deliveries.DropshipShipment;
import pl.commercelink.inventory.deliveries.DropshipShipmentResult;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveriesControllerDropshipShipmentTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDateTime SHIPPED_AT = LocalDateTime.of(2026, 8, 25, 10, 30);

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DropshipDeliveryCompletion dropshipDeliveryCompletion;
    @Mock
    private DeliveryReceptionService deliveryReceptionService;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private DeliveriesController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void stubSecurity() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
        when(messageSource.getMessage(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    private static Delivery dropshipDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        return delivery;
    }

    private static DeliveryAllocationsForm formFor(Delivery delivery, boolean withSelection) {
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        form.setStoreId(STORE_ID);
        form.setDeliveryId(delivery.getDeliveryId());
        form.setShipmentType(ShipmentType.Courier);
        form.setShipmentCarrier("DPD");
        form.setShipmentTrackingNo("PKG-1");
        form.setShipmentShippedAt(SHIPPED_AT);
        if (withSelection) {
            Order order = new Order(STORE_ID);
            order.setOrderId("order-1");
            BillingDetails billingDetails = new BillingDetails();
            billingDetails.setEmail("customer@example.com");
            order.setBillingDetails(billingDetails);
            OrderItem item = new OrderItem("order-1", "Category", "Product", 1, 100.0, null, false);
            item.setItemId("item-1");
            item.setDeliveryId(delivery.getDeliveryId());
            item.setStatus(FulfilmentStatus.Ordered);
            item.setEan("5900000000001");
            item.setManufacturerCode("MFN-1");
            Allocation allocation = Allocation.fromOrderItem(order, item);
            allocation.setSelected(true);
            form.setAllocations(List.of(allocation));
        }
        return form;
    }

    private String expectedRedirect(Delivery delivery) {
        return "redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId();
    }

    @Test
    void storeAdminConfirmsShipmentWithTheSubmittedParcelData() {
        // given
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(dropshipDeliveryCompletion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any()))
                .thenReturn(OperationResult.success(DropshipShipmentResult.COMPLETED));

        // when
        String view = controller.confirmDropshipShipment(formFor(delivery, true), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo(expectedRedirect(delivery));
        ArgumentCaptor<DropshipShipment> shipment = ArgumentCaptor.forClass(DropshipShipment.class);
        verify(dropshipDeliveryCompletion).confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), shipment.capture());
        assertThat(shipment.getValue())
                .isEqualTo(new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-1", null, SHIPPED_AT));
        verify(redirectAttributes).addFlashAttribute("successMessage", "deliveries.dropship.shipment.success");
    }

    @Test
    void storeAdminRouteIgnoresTheStoreIdCarriedByTheForm() {
        // given
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(dropshipDeliveryCompletion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any()))
                .thenReturn(OperationResult.success(DropshipShipmentResult.COMPLETED));
        DeliveryAllocationsForm form = formFor(delivery, true);
        form.setStoreId("other-store");

        // when
        controller.confirmDropshipShipment(form, redirectAttributes, Locale.ENGLISH);

        // then
        verify(deliveriesRepository).findById(STORE_ID, delivery.getDeliveryId());
        verify(deliveriesRepository, never()).findById(eq("other-store"), any());
        verify(dropshipDeliveryCompletion).confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any());
    }

    @Test
    void partialConfirmationReportsThatTheOrderStillWaits() {
        // given
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(dropshipDeliveryCompletion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any()))
                .thenReturn(OperationResult.success(DropshipShipmentResult.PARTIAL));

        // when
        controller.confirmDropshipShipment(formFor(delivery, true), redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("successMessage", "deliveries.dropship.shipment.success.partial");
    }

    @Test
    void superAdminRouteConfirmsForTheGivenStore() {
        // given
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(dropshipDeliveryCompletion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any()))
                .thenReturn(OperationResult.success(DropshipShipmentResult.COMPLETED));

        // when
        String view = controller.confirmDropshipShipmentForSuperAdmin(STORE_ID, formFor(delivery, true),
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(dropshipDeliveryCompletion).confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any());
    }

    @Test
    void warehouseDeliveryIsRejected() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.confirmDropshipShipment(formFor(delivery, true), redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.shipment.error.notDropship");
        verifyNoInteractions(dropshipDeliveryCompletion);
    }

    @Test
    void supplierOrderInFlightIsRejected() {
        // given
        Delivery delivery = dropshipDelivery();
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.confirmDropshipShipment(formFor(delivery, true), redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.confirm.unavailable");
        verifyNoInteractions(dropshipDeliveryCompletion);
    }

    @Test
    void alreadyReceivedDeliveryIsRejected() {
        // given
        Delivery delivery = dropshipDelivery();
        delivery.markAsReceived();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.confirmDropshipShipment(formFor(delivery, true), redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.confirm.unavailable");
        verifyNoInteractions(dropshipDeliveryCompletion);
    }

    @Test
    void missingSelectionIsRejected() {
        // given
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.confirmDropshipShipment(formFor(delivery, false), redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.select.at.least.one");
        verifyNoInteractions(dropshipDeliveryCompletion);
    }

    @Test
    void invalidParcelDataIsRejectedBeforeTheService() {
        // given
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryAllocationsForm form = formFor(delivery, true);
        form.setShipmentTrackingNo(" ");

        // when
        controller.confirmDropshipShipment(form, redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.shipment.error.trackingNo");
        verifyNoInteractions(dropshipDeliveryCompletion);
    }

    @Test
    void serviceFailureIsShownAsAnError() {
        // given
        Delivery delivery = dropshipDelivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(dropshipDeliveryCompletion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any()))
                .thenReturn(OperationResult.failure("deliveries.dropship.shipment.error.orderCancelled"));

        // when
        controller.confirmDropshipShipment(formFor(delivery, true), redirectAttributes, Locale.ENGLISH);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.shipment.error.orderCancelled");
    }
}
