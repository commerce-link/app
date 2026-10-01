package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.orders.*;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

// StoresRepository, Store, Delivery, Allocation, SupplierDeliveryAddress, ConcurrentModel, Model, Set

@ExtendWith(MockitoExtension.class)
class DeliveryScopesTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String PROVIDER = "Acme";

    @Mock private DeliveriesPlanningService planning;
    @Mock private RestockSuggestionService restockSuggestions;
    @Mock private DeliveryTaxResolver taxResolver;
    @Mock private SupplierPurchaseService supplierPurchase;
    @Mock private DeliveryCreationService deliveryCreation;
    @Mock private StoresRepository stores;
    @Mock private OrdersRepository orders;
    @Mock private OrderItemsRepository orderItems;
    @Mock private DropshipEligibility eligibility;
    @Mock private DropshipPurchaseService dropshipPurchase;

    @InjectMocks private DeliveryScopes scopes;

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
    void withoutAnOrderTheScopeIsTheWarehouseBatchOfTheSupplier() {
        // given
        Order order = order();
        Delivery planned = new Delivery(STORE_ID, null, PROVIDER);
        Allocation allocation = Allocation.fromOrderItem(order, allocatedItem("item-1", 2));
        planned.setAllocations(new ArrayList<>(List.of(allocation)));
        when(planning.run(STORE_ID, PROVIDER)).thenReturn(planned);
        when(taxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, null)).scope();
        DeliveryCreationForm form = scope.plannedForm();

        // then
        assertThat(scope.dropship()).isFalse();
        assertThat(scope.requiresOrderIdentity()).isTrue();
        assertThat(form.getProvider()).isEqualTo(PROVIDER);
        assertThat(form.getTax()).isEqualTo(1.23);
        assertThat(form.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getAllocations()).allMatch(Allocation::isSelected));
        verify(restockSuggestions).suggestForDelivery(eq(STORE_ID), eq(PROVIDER), any());
        verifyNoInteractions(eligibility);
    }

    @Test
    void warehouseWithNothingPlannedHasNoForm() {
        // given
        when(planning.run(STORE_ID, PROVIDER)).thenReturn(null);

        // when
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, null)).scope();

        // then
        assertThat(scope.plannedForm()).isNull();
    }

    @Test
    void anOrderMakesTheScopeDropshipWithTheOrdersLinesOfThatSupplier() {
        // given
        Order order = order();
        OrderItem other = allocatedItem("item-2", 1);
        other.setDeliveryId("OtherSupplier");
        when(orders.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItems.findByOrderId(ORDER_ID)).thenReturn(List.of(allocatedItem("item-1", 2), other));
        when(eligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        when(taxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, ORDER_ID)).scope();
        DeliveryCreationForm form = scope.plannedForm();

        // then
        assertThat(scope.dropship()).isTrue();
        assertThat(scope.order()).isSameAs(order);
        assertThat(scope.requiresOrderIdentity()).isFalse();
        assertThat(scope.purchaseAvailable()).isTrue();
        assertThat(form.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getRequestedQty()).isEqualTo(2));
        verifyNoInteractions(planning);
    }

    @Test
    void anOrderThatCannotBeDropshippedIsRefusedWithItsReason() {
        // given
        Order order = order();
        when(orders.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(eligibility.assess(same(order), any())).thenReturn(DropshipAssessment.rejected(DropshipRejection.NO_SHIPPING_DETAILS));

        // when / then
        assertThat(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID))
                .isEqualTo(new DeliveryScopes.Resolution.Refused("orders.dropship.rejected.noShippingDetails"));
    }

    @Test
    void anotherSupplierThanTheAssessmentAcceptsIsAMismatch() {
        // given
        Order order = order();
        when(orders.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(eligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of("Other")));

        // when / then
        assertThat(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID))
                .isEqualTo(new DeliveryScopes.Resolution.Refused("orders.dropship.rejected.providerMismatch"));
    }

    @Test
    void aMissingOrderIsRefusedWithoutAMessage() {
        // given
        when(orders.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when / then
        assertThat(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID)).isEqualTo(new DeliveryScopes.Resolution.Refused(null));
    }

    @Test
    void warehousePurchaseModelPreselectsTheAddressMatchingTheStoresShippingDetails() {
        // given
        when(supplierPurchase.requiresApproval(STORE_ID, PROVIDER)).thenReturn(false);
        List<SupplierDeliveryAddress> addresses = List.of(
                new SupplierDeliveryAddress("a-1", "ul. Polna 1", "Warszawa", "00-001", "PL"),
                new SupplierDeliveryAddress("a-2", "ul. Składowa 12", "Pruszków", "05-800", "PL"));
        when(supplierPurchase.deliveryAddresses(STORE_ID, PROVIDER)).thenReturn(addresses);
        Store store = new Store();
        store.addShippingDetails(order().getShippingDetails(), true);   // ul. Polna 1, 00-001 Warszawa
        when(stores.findById(STORE_ID)).thenReturn(store);
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, null)).scope();
        DeliveryCreationForm form = new DeliveryCreationForm();
        Model model = new ConcurrentModel();

        // when
        scope.addPurchaseModel(form, model);

        // then
        assertThat(model.getAttribute("deliveryAddresses")).isEqualTo(addresses);
        assertThat(form.getDeliveryAddressId()).isEqualTo(SuggestedDeliveryAddress
                .match(store.getDefaultShippingDetails(), addresses).orElse(null));
    }

    @Test
    void warehousePurchaseModelReportsAnAddressBookFailureAndSkipsAddressesForApproval() {
        // given
        when(supplierPurchase.requiresApproval(STORE_ID, PROVIDER)).thenReturn(false, true);
        when(supplierPurchase.deliveryAddresses(STORE_ID, PROVIDER)).thenThrow(new IllegalStateException("timeout"));
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, null)).scope();
        Model failed = new ConcurrentModel();
        Model approval = new ConcurrentModel();

        // when
        scope.addPurchaseModel(new DeliveryCreationForm(), failed);
        scope.addPurchaseModel(new DeliveryCreationForm(), approval);

        // then
        assertThat(failed.getAttribute("deliveryAddressError")).isEqualTo("timeout");
        assertThat(failed.getAttribute("deliveryAddresses")).isEqualTo(List.of());
        assertThat(approval.getAttribute("requiresApproval")).isEqualTo(true);
        assertThat(approval.containsAttribute("deliveryAddresses")).isFalse();
    }

    @Test
    void savingDelegatesToTheServiceOfEachScope() {
        // given
        Order order = order();
        when(orders.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(eligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));
        DeliveryCreationForm form = new DeliveryCreationForm();
        when(deliveryCreation.run(STORE_ID, form)).thenReturn("d-w");
        when(dropshipPurchase.createManualDropship(STORE_ID, order, form)).thenReturn(OperationResult.success("d-d"));

        // when
        OperationResult<String> warehouse = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, null)).scope().save(form);
        OperationResult<String> dropship = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, ORDER_ID)).scope().save(form);

        // then
        assertThat(warehouse.getPayload()).isEqualTo("d-w");
        assertThat(dropship.getPayload()).isEqualTo("d-d");
    }

    @Test
    void validatingAnUnavailableIntegrationThrowsSoThePageShowsTheRetry() {
        // given
        when(supplierPurchase.isOrderingAvailable(STORE_ID, PROVIDER)).thenReturn(false);
        DeliveryScope scope = ((DeliveryScopes.Resolution.Found) scopes.resolve(STORE_ID, PROVIDER, null)).scope();

        // when / then
        assertThatThrownBy(() -> scope.validate(new DeliveryCreationForm())).isInstanceOf(IllegalStateException.class);
    }
}
