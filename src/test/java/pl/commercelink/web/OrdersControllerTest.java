package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private OrdersManager ordersManager;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private OrdersController ordersController;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setupStoreId() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    @DisplayName("updateAddressDetails persists new billing details on order that is not yet invoiced")
    void updateAddressDetailsSetsBillingDetailsOnNonInvoicedOrderAndSaves() {
        // given
        Order existingOrder = orderBase();
        BillingDetails newBilling = new BillingDetails();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getBillingDetails().getCity()).isEqualTo("Krakow");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("updateAddressDetails sets locked-billing flash message and does not save when order is already invoiced")
    void updateAddressDetailsSetsFlashMessageAndSkipsSaveWhenOrderAlreadyInvoiced() {
        // given
        Order existingOrder = invoicedOrder();
        BillingDetails newBilling = new BillingDetails();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.billing.details.locked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Billing locked");

        // when
        ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Billing locked"));
    }

    @Test
    @DisplayName("updateAddressDetails persists new shipping details when type is shipping")
    void updateAddressDetailsSetsShippingDetailsAndSavesWhenTypeIsShipping() {
        // given
        Order existingOrder = orderBase();
        ShippingDetails newShipping = new ShippingDetails();
        newShipping.setCity("Wroclaw");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShippingDetails(newShipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateAddressDetails(ORDER_ID, "shipping", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getShippingDetails().getCity()).isEqualTo("Wroclaw");
    }

    @Test
    @DisplayName("updateShipments publishes ShipmentCreated when the saved order has a shipment with shipping data")
    void updateShipmentsPublishesShipmentCreatedWhenShippingDataPresent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments does not publish ShipmentCreated when no shipment has shipping data")
    void updateShipmentsSkipsPublishWhenShippingDataAbsent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.PersonalCollection);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    private Order orderBase() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        return order;
    }

    private Order invoicedOrder() {
        Order order = orderBase();
        order.addDocument(new pl.commercelink.documents.Document(
                "fv-1", "FV/1/2026", "https://example.com/fv/1",
                pl.commercelink.documents.DocumentType.InvoiceVat));
        return order;
    }
}
