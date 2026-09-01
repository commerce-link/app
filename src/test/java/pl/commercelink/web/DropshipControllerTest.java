package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.DeliveryTaxResolver;
import pl.commercelink.inventory.deliveries.DropshipAssessment;
import pl.commercelink.inventory.deliveries.DropshipRejection;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.DropshipPurchaseService;
import pl.commercelink.inventory.deliveries.PurchaseSubmission;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String PROVIDER = "Acme";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private DropshipEligibility dropshipEligibility;
    @Mock
    private SupplierPurchaseService supplierPurchaseService;
    @Mock
    private DropshipPurchaseService dropshipPurchaseService;
    @Mock
    private DeliveryTaxResolver deliveryTaxResolver;
    @Mock
    private MessageSource messageSource;
    @Mock
    private org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes;

    @InjectMocks
    private DropshipController controller;

    private static Order order() {
        Order order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("ul. Polna 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setPhone("+48601234567");
        details.setEmail("jan@example.com");
        order.setShippingDetails(details);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("jan@example.com");
        order.setBillingDetails(billing);
        return order;
    }

    private static OrderItem allocatedItem(String itemId, int qty) {
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER_ID);
        item.setItemId(itemId);
        item.setDeliveryId(PROVIDER);
        item.setStatus(FulfilmentStatus.Allocation);
        item.setEan("5900000000001");
        item.setManufacturerCode("MFN-1");
        item.setName("Product");
        item.setQty(qty);
        return item;
    }

    @Test
    void createScreenBuildsTheFormFromTheOrdersAllocations() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(
                List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        Model model = new ConcurrentModel();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.dropshipCreate(ORDER_ID, model);
        }

        // then
        assertThat(view).isEqualTo("dropshipCreate");
        DeliveryCreationForm form = (DeliveryCreationForm) model.getAttribute("form");
        assertThat(form.getProvider()).isEqualTo(PROVIDER);
        assertThat(form.getItems()).hasSize(1);
        DeliveryItem item = form.getItems().getFirst();
        assertThat(item.getRequestedQty()).isEqualTo(2);
        assertThat(item.getAllocations()).allMatch(allocation -> allocation.isSelected());
        assertThat(model.getAttribute("consignee")).isSameAs(order.getShippingDetails());
    }

    @Test
    void createScreenExposesThePickupShipmentAndTheBlockedReason() {
        // given
        Order order = order();
        Shipment shipment = new Shipment();
        shipment.setType(ShipmentType.PickupPoint);
        shipment.setCarrier("InPost");
        shipment.setCollectionPointCode("WAW04A");
        order.addShipment(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        when(dropshipPurchaseService.purchaseBlockedReason(STORE_ID, order, PROVIDER))
                .thenReturn("orders.dropship.error.pickupPointUnsupported");
        Model model = new ConcurrentModel();

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.dropshipCreate(ORDER_ID, model);
        }

        // then
        assertThat(model.getAttribute("pickupShipment")).isSameAs(shipment);
        assertThat(model.getAttribute("purchaseBlockedReason")).isEqualTo("orders.dropship.error.pickupPointUnsupported");
    }

    @Test
    void backFromConfirmationRestoresTheUserEnteredHeaderAndSelections() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(
                List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        DeliveryCreationForm posted = new DeliveryCreationForm();
        posted.setExternalDeliveryId("EXT-9");
        posted.setShippingCost(15.0);
        posted.setPaymentTerms(30);
        Model model = new ConcurrentModel();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.backFromDropshipConfirmation(ORDER_ID, posted, model);
        }

        // then
        assertThat(view).isEqualTo("dropshipCreate");
        DeliveryCreationForm form = (DeliveryCreationForm) model.getAttribute("form");
        assertThat(form.getExternalDeliveryId()).isEqualTo("EXT-9");
        assertThat(form.getShippingCost()).isEqualTo(15.0);
        assertThat(form.getPaymentTerms()).isEqualTo(30);
    }

    @Test
    void purchasePostShowsTheConfirmationWithAFreshPurchaseRef() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        Model model = new ConcurrentModel();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.dropshipPurchase(ORDER_ID, form, model);
        }

        // then
        assertThat(view).isEqualTo("dropshipConfirmation");
        assertThat(model.getAttribute("purchaseRef")).isNotNull();
        assertThat(model.getAttribute("form")).isSameAs(form);
    }

    @Test
    void manualCreateRedirectsToTheCreatedDelivery() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        when(dropshipPurchaseService.createManualDropship(eq(STORE_ID), same(order), same(form)))
                .thenReturn(OperationResult.success("delivery-7"));

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.createManualDropship(ORDER_ID, form, redirectAttributes, Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=delivery-7");
    }

    @Test
    void manualCreateWithNothingSelectedAndRemoveUnselectedReleasesTheAllocationsAndReturnsToTheOrder() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        form.setRemoveUnselected(true);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(0);
        form.getItems().add(item);
        when(messageSource.getMessage(eq("orders.dropship.unselectedReleased"), any(), any())).thenReturn("released");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.createManualDropship(ORDER_ID, form, redirectAttributes, Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(dropshipPurchaseService).releaseUnselected(STORE_ID, form);
        verify(dropshipPurchaseService, never()).createManualDropship(any(), any(), any());
        verify(redirectAttributes).addFlashAttribute("successMessage", "released");
    }

    @Test
    void manualCreateWithNothingSelectedAndNoRemovalGoesBackToTheFormWithTheError() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(0);
        form.getItems().add(item);
        when(dropshipPurchaseService.createManualDropship(eq(STORE_ID), same(order), same(form)))
                .thenReturn(OperationResult.failure("orders.dropship.error.nothingSelected"));
        when(messageSource.getMessage(eq("orders.dropship.error.nothingSelected"), any(), any())).thenReturn("nothing");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.createManualDropship(ORDER_ID, form, redirectAttributes, Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID + "/dropship");
        verify(dropshipPurchaseService, never()).releaseUnselected(any(), any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "nothing");
    }

    @Test
    void createScreenRedirectsBackWhenTheOrderIsNotEligible() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED));

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.dropshipCreate(ORDER_ID, new ConcurrentModel());
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void confirmSubmitsTheDropshipAndRedirectsToTheDelivery() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        when(dropshipPurchaseService.submitDropship(eq(STORE_ID), same(order), same(form), eq("ref-1")))
                .thenReturn(OperationResult.success(new PurchaseSubmission("delivery-9", false)));

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.confirmDropship(ORDER_ID, "ref-1", form, new ConcurrentModel(), Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=delivery-9");
        verify(dropshipPurchaseService).submitDropship(eq(STORE_ID), same(order), same(form), eq("ref-1"));
    }

    @Test
    void confirmRejectsAStaleFormWhoseAllocationMovedElsewhere() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.confirmDropship(ORDER_ID, "ref-1", form, new ConcurrentModel(), Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(dropshipPurchaseService, never()).submitDropship(any(), any(), any(), any());
    }

    @Test
    void confirmRejectsAFormNamingADifferentProviderThanTheAllocation() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of("Elko")));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.confirmDropship(ORDER_ID, "ref-1", form, new ConcurrentModel(), Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(dropshipPurchaseService, never()).submitDropship(any(), any(), any(), any());
    }

    @Test
    void failedSubmissionReturnsToTheConfirmationWithTheError() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        when(dropshipPurchaseService.submitDropship(eq(STORE_ID), same(order), same(form), eq("ref-1")))
                .thenReturn(OperationResult.failure("orders.dropship.error.unsupported"));
        when(messageSource.getMessage(eq("orders.dropship.error.unsupported"), any(), any()))
                .thenReturn("unsupported");
        Model model = new ConcurrentModel();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.confirmDropship(ORDER_ID, "ref-1", form, model, Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("dropshipConfirmation");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("unsupported");
    }
}
