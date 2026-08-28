package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.orders.Order;
import pl.commercelink.starter.util.OperationResult;

import java.time.LocalDate;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseServiceDropshipTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String DELIVERY_ID = "delivery-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private SupplierProviderResolver supplierProviderResolver;
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
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DropshipPurchaseService dropshipPurchaseService;
    @Mock
    private DropshipOrderLocator dropshipOrderLocator;
    @Mock
    private OrderAllocationsManager orderAllocationsManager;

    @InjectMocks
    private SupplierPurchaseService service;

    private final Store store = new Store();

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(supplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(supplierProvider);
        lenient().when(supplierSkuResolver.forStore(anyString(), anyString())).thenReturn((ean, mfn) -> "ACME-" + ean);
    }

    private void connectSupplier(ConnectionMode mode) {
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setSupplierConnections(List.of(new StoreSupplierConnection(PROVIDER, mode)));
        store.setFulfilmentConfiguration(fulfilment);
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
        delivery.setType(DeliveryType.DROPSHIP);
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
    void processPendingRoutesDropshipPlacementWithoutCompletingTheOrder() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(dropshipPurchaseService.placeDropshipOrder(eq(STORE_ID), same(delivery), anyList(), eq(ORDER_ID)))
                .thenReturn(new SupplierOrderResult(
                        "ACME-DS-ref-1", 220.0, "PLN",
                        List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID, ORDER_ID, 1);

        // then
        verify(dropshipPurchaseService).placeDropshipOrder(eq(STORE_ID), same(delivery), anyList(), eq(ORDER_ID));
        verify(supplierProvider, never()).placeOrder(any());
        verify(deliveryCreationService).completeDropshipPending(eq(STORE_ID), same(delivery), any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
        assertTrue(delivery.hasEvent("DELIVERY_ORDERED_AUTOMATICALLY"));
    }

    @Test
    void processPendingMarksDropshipDeliveryFailedWhenPlacementThrows() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(dropshipPurchaseService.placeDropshipOrder(eq(STORE_ID), same(delivery), anyList(), eq(ORDER_ID)))
                .thenThrow(new SupplierOrderException("Order " + ORDER_ID
                        + " has no complete shipping details for a dropship purchase"));

        // when
        service.processPending(STORE_ID, DELIVERY_ID, ORDER_ID, 1);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("Order " + ORDER_ID + " has no complete shipping details for a dropship purchase",
                delivery.getOrderErrorMessage());
        verify(deliveryCreationService, never()).releaseAllocations(any(), any());
    }

    @Test
    void payloadOrderIdSkipsIndexDiscovery() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(dropshipPurchaseService.placeDropshipOrder(eq(STORE_ID), same(delivery), anyList(), eq(ORDER_ID)))
                .thenReturn(new SupplierOrderResult(
                        "ACME-DS-ref-1", 220.0, "PLN",
                        List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID, ORDER_ID, 1);

        // then
        verify(dropshipPurchaseService).placeDropshipOrder(eq(STORE_ID), same(delivery), anyList(), eq(ORDER_ID));
        verifyNoInteractions(dropshipOrderLocator);
    }

    @Test
    void blankPayloadOrderIdFallsBackToDiscovery() throws Exception {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.empty());

        // when / then
        assertThrows(DropshipOrderPendingException.class,
                () -> service.processPending(STORE_ID, DELIVERY_ID, "  ", 1));
        verify(dropshipOrderLocator).locate(DELIVERY_ID);
        verify(dropshipPurchaseService, never()).placeDropshipOrder(any(), any(), any(), any());
    }

    @Test
    void emptyLocatorAnswerBelowCapIsRetryable() {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.empty());

        // when / then
        assertThrows(DropshipOrderPendingException.class,
                () -> service.processPending(STORE_ID, DELIVERY_ID, null, 1));
        assertNotEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        verify(deliveriesRepository, never()).save(any());
        verify(dropshipPurchaseService, never()).placeDropshipOrder(any(), any(), any(), any());
    }

    @Test
    void emptyLocatorAnswerAtCapFailsTheDelivery() {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.empty());

        // when
        service.processPending(STORE_ID, DELIVERY_ID, null, SupplierPurchaseService.MAX_SQS_ATTEMPTS);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("Dropship order could not be resolved for delivery " + DELIVERY_ID,
                delivery.getOrderErrorMessage());
        verify(deliveriesRepository).save(delivery);
        verify(dropshipPurchaseService, never()).placeDropshipOrder(any(), any(), any(), any());
    }

    @Test
    void locatorInvariantViolationFailsHard() {
        // given
        connectSupplier(ConnectionMode.OWN);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenThrow(
                new IllegalStateException("Dropship delivery " + DELIVERY_ID + " is claimed by orders [a, b]"));

        // when
        service.processPending(STORE_ID, DELIVERY_ID, null, 1);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("Dropship delivery " + DELIVERY_ID + " is claimed by orders [a, b]",
                delivery.getOrderErrorMessage());
        verify(dropshipPurchaseService, never()).placeDropshipOrder(any(), any(), any(), any());
    }

    @Test
    void dropshipApprovalDoesNotRequireDeliveryAddress() throws Exception {
        // given
        connectSupplier(ConnectionMode.GLOBAL);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipPurchaseService.orderOf(STORE_ID, delivery)).thenReturn(Optional.of(new Order()));
        lenient().when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, null);

        // then
        assertTrue(result.isSuccess());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, delivery.getOrderStatus());
        verify(supplierPurchaseEventPublisher).publish(any(), any());
    }

    @Test
    void dropshipApprovalRequiresOptionsButNotAnAddress() {
        // given
        connectSupplier(ConnectionMode.GLOBAL);
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        lenient().when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        when(dropshipPurchaseService.orderOf(STORE_ID, delivery)).thenReturn(Optional.of(order));
        List<SupplierOrderOption> laneOption = List.of(new SupplierOrderOption("lane", "Lane",
                List.of(new SupplierOrderOptionChoice("fast", "Fast", null)), "fast", true));
        when(supplierProvider.orderOptions(any())).thenReturn(laneOption);

        // when
        OperationResult<String> missingOptions = service.approve(STORE_ID, DELIVERY_ID, null, Map.of());

        // then
        assertFalse(missingOptions.isSuccess());
        assertEquals("deliveries.purchase.error.options", missingOptions.getMessage());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, delivery.getOrderStatus());

        // when
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        OperationResult<String> success = service.approve(STORE_ID, DELIVERY_ID, null, Map.of("lane", "fast"));

        // then
        assertTrue(success.isSuccess());
        assertEquals(Map.of("lane", "fast"), delivery.getSupplierOrderChoices());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, delivery.getOrderStatus());
    }

    @Test
    void completeManuallyOnFailedDropshipReopensTheShipmentConfirmation() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        Delivery delivery = pendingDropshipDelivery(form, "ref-1");
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setOrderErrorMessage("boom");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        LocalDate estimatedDeliveryAt = LocalDate.of(2026, 9, 1);

        // when
        OperationResult<String> result = service.completeManually(STORE_ID, DELIVERY_ID, "ACME-PHONE-1", estimatedDeliveryAt);

        // then
        assertTrue(result.isSuccess());
        assertTrue(delivery.isDropship());
        assertNull(delivery.getOrderStatus());
        assertNull(delivery.getOrderErrorMessage());
        assertFalse(delivery.hasBeenReceived());
        assertEquals("ACME-PHONE-1", delivery.getExternalDeliveryId());
        verify(orderAllocationsManager).commit(eq(STORE_ID), eq(DELIVERY_ID), eq(estimatedDeliveryAt), anyList());
        verifyNoInteractions(supplierProvider, dropshipPurchaseService, supplierPurchaseEventPublisher);
    }

}
