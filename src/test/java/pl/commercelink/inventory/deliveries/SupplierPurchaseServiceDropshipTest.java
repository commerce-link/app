package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.GlobalSupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseServiceDropshipTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String DELIVERY_ID = "delivery-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DeliveryCreationService deliveryCreationService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DeliveryTaxResolver deliveryTaxResolver;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierProvider supplierProvider;
    @Mock
    private SupplierSkuResolver supplierSkuResolver;
    @Mock
    private SupplierPurchaseEventPublisher supplierPurchaseEventPublisher;
    @Mock
    private ExchangeRates exchangeRates;
    @Mock
    private GlobalSupplierProviderFactory globalSupplierProviderFactory;
    @Mock
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private DropshipOrderCompletion dropshipOrderCompletion;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;

    @InjectMocks
    private SupplierPurchaseService service;

    private final Store store = new Store();

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(supplierProviderFactory.get(store, PROVIDER)).thenReturn(supplierProvider);
        lenient().when(globalSupplierProviderFactory.get(PROVIDER)).thenReturn(Optional.of(supplierProvider));
        lenient().when(supplierSkuResolver.forStore(anyString(), anyString())).thenReturn((ean, mfn) -> "ACME-" + ean);
    }

    private void connectSupplier(ConnectionMode mode) {
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setSupplierConnections(List.of(new StoreSupplierConnection(PROVIDER, mode)));
        store.setFulfilmentConfiguration(fulfilment);
    }

    private static ShippingDetails shippingDetails() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("ul. Polna 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setPhone("+48601234567");
        details.setEmail("jan.kowalski@example.com");
        return details;
    }

    private static Order directToConsumerOrder() {
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        order.setShippingDetails(shippingDetails());
        return order;
    }

    private DeliveryCreationForm formWithItem(String ean, String mfn, int requestedQty, double unitCost) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        DeliveryItem item = new DeliveryItem();
        item.setName("Product " + ean);
        item.setEan(ean);
        item.setMfn(mfn);
        item.setRequestedQty(requestedQty);
        item.setUnitCost(unitCost);
        form.getItems().add(item);
        return form;
    }

    private Delivery pendingDropshipDelivery(DeliveryCreationForm form, String purchaseRef) {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        delivery.setDropshipOrderId(ORDER_ID);
        lenient().when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID))
                .thenReturn(deliveryWithAllocations(form));
        return delivery;
    }

    private Delivery deliveryWithAllocations(DeliveryCreationForm form) {
        List<Allocation> allocations = form.getItems().stream()
                .map(item -> {
                    Allocation allocation = new Allocation();
                    allocation.setKey(new AllocationKey(ORDER_ID, java.util.UUID.randomUUID().toString(), "customer"));
                    allocation.setType(AllocationType.Order);
                    allocation.setName(item.getName());
                    allocation.setEan(item.getEan());
                    allocation.setMfn(item.getMfn());
                    allocation.setUnitCost(item.getUnitCost());
                    allocation.setQty(item.getRequestedQty());
                    return allocation;
                })
                .toList();
        Delivery withAllocations = new Delivery();
        withAllocations.setDeliveryId(DELIVERY_ID);
        withAllocations.setProvider(PROVIDER);
        withAllocations.setItems(DeliveryItem.groupAndUnify(allocations));
        return withAllocations;
    }

    @Test
    void dropshipAvailableWhenProviderSupportsIt() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);

        // when / then
        assertTrue(service.isDropshipAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void dropshipUnavailableWhenProviderDoesNotSupportIt() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(false);

        // when / then
        assertFalse(service.isDropshipAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void submitDropshipCreatesAwaitingApprovalDeliveryForGlobalSupplier() {
        // given
        connectSupplier(ConnectionMode.GLOBAL);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);
        when(supplierConnectionModeResolver.resolve(store, PROVIDER)).thenReturn(ConnectionMode.GLOBAL);
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, directToConsumerOrder(), form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(ORDER_ID, saved.getValue().getDropshipOrderId());
        assertTrue(saved.getValue().isDropship());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, saved.getValue().getOrderStatus());
        assertEquals(ConnectionMode.GLOBAL, saved.getValue().getConnectionMode());
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void submitDropshipPublishesImmediatelyForOwnSupplier() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(true);
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, directToConsumerOrder(), form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, saved.getValue().getOrderStatus());
        verify(supplierPurchaseEventPublisher).publish(any());
    }

    @Test
    void submitDropshipRejectsWarehouseFulfilmentOrder() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.fulfilmentType", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void submitDropshipRejectsOrderWithoutShippingDetails() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.setShippingDetails(null);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.address", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void submitDropshipRejectsProviderWithoutDropshipSupport() {
        // given
        connectSupplier(ConnectionMode.OWN);
        when(supplierProvider.supportsDropshipping()).thenReturn(false);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, directToConsumerOrder(), formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.unsupported", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void processPendingPlacesDropshipOrderWithConsigneeFromOrder() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(directToConsumerOrder());
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeDropshipOrder(any())).thenReturn(new SupplierOrderResult(
                "ACME-DS-ref-1", 220.0, "PLN",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        ArgumentCaptor<SupplierDropshipRequest> request = ArgumentCaptor.forClass(SupplierDropshipRequest.class);
        verify(supplierProvider).placeDropshipOrder(request.capture());
        verify(supplierProvider, never()).placeOrder(any());
        SupplierConsignee consignee = request.getValue().consignee();
        assertEquals("Jan", consignee.firstName());
        assertEquals("Kowalski", consignee.lastName());
        assertEquals("ul. Polna 1", consignee.streetAndNumber());
        assertEquals("+48601234567", consignee.phone());
        assertEquals("jan.kowalski@example.com", consignee.email());
        assertEquals("ref-1", request.getValue().clientOrderRef());
        verify(deliveryCreationService).completeDropshipPending(eq(STORE_ID), same(delivery), any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
        verify(dropshipOrderCompletion).markSuppliedByDropship(STORE_ID, ORDER_ID, DELIVERY_ID);
        assertTrue(delivery.hasEvent("DELIVERY_ORDERED_AUTOMATICALLY"));
    }

    @Test
    void processPendingFailsDropshipDeliveryWhenOrderIsGone() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        verify(supplierProvider, never()).placeDropshipOrder(any());
        verify(dropshipOrderCompletion, never()).markSuppliedByDropship(any(), any(), any());
        verify(deliveryCreationService, never()).releaseAllocations(any(), any());
    }

    @Test
    void submitDropshipRejectsAConsigneeTheSupplierContractWouldRefuse() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.getShippingDetails().setSurname(null);

        // when
        OperationResult<PurchaseSubmission> result = service.submitDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0), "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("orders.dropship.error.address", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void processPendingFailsDropshipDeliveryWhenTheConsigneeTurnedInvalid() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        Order order = directToConsumerOrder();
        order.getShippingDetails().setSurname(null);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        verify(supplierProvider, never()).placeDropshipOrder(any());
        verify(deliveryCreationService, never()).releaseAllocations(any(), any());
    }

    @Test
    void createManualDropshipCreatesASettledDeliveryAndMarksItemsSupplied() {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        form.setExternalDeliveryId("PHONE-123");
        when(supplierConnectionModeResolver.resolve(store, PROVIDER)).thenReturn(ConnectionMode.OWN);

        // when
        OperationResult<String> result = service.createManualDropship(STORE_ID, directToConsumerOrder(), form);

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        Delivery delivery = saved.getValue();
        assertEquals(ORDER_ID, delivery.getDropshipOrderId());
        assertEquals("PHONE-123", delivery.getExternalDeliveryId());
        assertNull(delivery.getOrderStatus());
        verify(deliveryCreationService).claimAllocations(eq(STORE_ID), same(delivery), same(form));
        verify(dropshipOrderCompletion).markSuppliedByDropship(STORE_ID, ORDER_ID, delivery.getDeliveryId());
        verify(supplierPurchaseEventPublisher, never()).publish(any());
        verify(supplierProvider, never()).placeDropshipOrder(any());
    }

    @Test
    void createManualDropshipRejectsWarehouseFulfilmentOrder() {
        // given
        connectSupplier(ConnectionMode.OWN);
        Order order = directToConsumerOrder();
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        OperationResult<String> result = service.createManualDropship(
                STORE_ID, order, formWithItem("EAN-1", "MFN-1", 2, 100.0));

        // then
        assertFalse(result.isSuccess());
        verify(deliveriesRepository, never()).save(any());
        verify(dropshipOrderCompletion, never()).markSuppliedByDropship(any(), any(), any());
    }

    @Test
    void toConsigneeNormalizesTheCountryCase() {
        // given
        ShippingDetails details = shippingDetails();
        details.setCountry("pl");

        // when
        SupplierConsignee consignee = SupplierPurchaseService.toConsignee(details);

        // then
        assertEquals("PL", consignee.country());
    }

    @Test
    void dropshipApprovalDoesNotRequireDeliveryAddress() throws Exception {
        // given
        connectSupplier(ConnectionMode.GLOBAL);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        lenient().when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, null);

        // then
        assertTrue(result.isSuccess());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, delivery.getOrderStatus());
        verify(supplierPurchaseEventPublisher).publish(any());
    }

    @Test
    void toConsigneeMapsShippingDetails() {
        // when
        SupplierConsignee consignee = SupplierPurchaseService.toConsignee(shippingDetails());

        // then
        assertEquals("Jan", consignee.firstName());
        assertEquals("Kowalski", consignee.lastName());
        assertEquals("ul. Polna 1", consignee.streetAndNumber());
        assertEquals("00-001", consignee.postalCode());
        assertEquals("Warszawa", consignee.city());
        assertEquals("PL", consignee.country());
        assertEquals("+48601234567", consignee.phone());
        assertEquals("jan.kowalski@example.com", consignee.email());
    }
}
