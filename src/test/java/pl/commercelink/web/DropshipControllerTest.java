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
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.PurchaseSubmission;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
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
    private DeliveryTaxResolver deliveryTaxResolver;
    @Mock
    private MessageSource messageSource;

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
    void confirmationScreenBuildsTheFormFromTheOrdersAllocations() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(
                List.of(allocatedItem("item-1", 2)));
        when(dropshipEligibility.eligibleProvider(same(order), any())).thenReturn(Optional.of(PROVIDER));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        Model model = new ConcurrentModel();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.dropshipConfirmation(ORDER_ID, model);
        }

        // then
        assertThat(view).isEqualTo("dropshipConfirmation");
        DeliveryCreationForm form = (DeliveryCreationForm) model.getAttribute("form");
        assertThat(form.getProvider()).isEqualTo(PROVIDER);
        assertThat(form.getItems()).hasSize(1);
        DeliveryItem item = form.getItems().getFirst();
        assertThat(item.getRequestedQty()).isEqualTo(2);
        assertThat(item.getAllocations()).allMatch(allocation -> allocation.isSelected());
        assertThat(model.getAttribute("consignee")).isSameAs(order.getShippingDetails());
        assertThat(model.getAttribute("purchaseRef")).isNotNull();
    }

    @Test
    void confirmationScreenRedirectsBackWhenTheOrderIsNotEligible() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(same(order), any())).thenReturn(Optional.empty());

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.dropshipConfirmation(ORDER_ID, new ConcurrentModel());
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void confirmSubmitsTheDropshipAndRedirectsToTheDelivery() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        when(supplierPurchaseService.submitDropship(eq(STORE_ID), same(order), same(form), eq("ref-1")))
                .thenReturn(OperationResult.success(new PurchaseSubmission("delivery-9", false)));

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.confirmDropship(ORDER_ID, "ref-1", form, new ConcurrentModel(), Locale.forLanguageTag("pl"));
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=delivery-9");
        verify(supplierPurchaseService).submitDropship(eq(STORE_ID), same(order), same(form), eq("ref-1"));
    }

    @Test
    void failedSubmissionReturnsToTheConfirmationWithTheError() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        when(supplierPurchaseService.submitDropship(eq(STORE_ID), same(order), same(form), eq("ref-1")))
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
