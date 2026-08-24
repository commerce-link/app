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
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String DELIVERY_ID = "delivery-1";

    @Mock
    private StoreSupplierProviderResolver storeSupplierProviderResolver;
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
    private OrderIdRefreshEventPublisher orderIdRefreshEventPublisher;
    @Mock
    private ExchangeRates exchangeRates;
    @Mock
    private SupplierProvider globalSupplierProvider;
    @Mock
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;

    @InjectMocks
    private SupplierPurchaseService service;

    private final Store store = new Store();

    @BeforeEach
    void setUp() {
        connectSupplier(ConnectionMode.OWN);
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(supplierProvider);
        lenient().when(supplierSkuResolver.forStore(anyString(), anyString())).thenReturn((ean, mfn) -> null);
    }

    private void connectSupplier(ConnectionMode mode) {
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setSupplierConnections(List.of(new StoreSupplierConnection(PROVIDER, mode)));
        store.setFulfilmentConfiguration(fulfilment);
    }

    private Store storeWithConnection(String provider, ConnectionMode mode) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setSupplierConnections(List.of(
                new StoreSupplierConnection(provider, mode, true, true)));
        store.setFulfilmentConfiguration(configuration);
        return store;
    }

    @Test
    void orderingUnavailableWhenSupplierIsConnectedGlobally() {
        // given
        connectSupplier(ConnectionMode.GLOBAL);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(null);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void orderingUnavailableWhenSupplierIsNotConnectedAtAll() {
        // given
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(null);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void orderingAvailableWhenProviderSupportsIt() {
        // given
        when(supplierProvider.supportsOrdering()).thenReturn(true);

        // when / then
        assertTrue(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void deliveryAddressesComeFromTheProvider() {
        // given
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.deliveryAddresses()).thenReturn(List.of(
                new SupplierDeliveryAddress("17200617", "ul. Łobzowska 22/1", "Kraków", "31-140", "PL")));

        // when
        List<SupplierDeliveryAddress> addresses = service.deliveryAddresses(STORE_ID, PROVIDER);

        // then
        assertEquals(1, addresses.size());
        assertEquals("17200617", addresses.getFirst().id());
    }

    @Test
    void deliveryAddressesAreEmptyWhenProviderDoesNotNeedOne() {
        // when / then
        assertTrue(service.deliveryAddresses(STORE_ID, PROVIDER).isEmpty());
        verify(supplierProvider, never()).deliveryAddresses();
    }

    @Test
    void deliveryAddressFailureIsNotSwallowed() {
        // given
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.deliveryAddresses()).thenThrow(new SupplierOrderException("403 Forbidden"));

        // when / then
        assertThrows(SupplierOrderException.class, () -> service.deliveryAddresses(STORE_ID, PROVIDER));
    }

    @Test
    void purchaseRefusedWhenRequiredDeliveryAddressMissing() {
        // given
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase(STORE_ID, form, "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.address", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void purchaseAcceptedWhenDeliveryAddressChosen() {
        // given
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        form.setDeliveryAddressId("17200617");
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-1")).thenReturn(Optional.empty());

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase(STORE_ID, form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        verify(deliveriesRepository).save(any());
    }

    @Test
    void orderingUnavailableWhenProviderMissing() {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(null);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void orderingUnavailableWhenFactoryThrows() {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenThrow(new RuntimeException("no credentials"));

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void ordersThroughTheGlobalProviderWhenTheConnectionIsGlobal() {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.supportsOrdering()).thenReturn(true);

        // when
        boolean available = service.isOrderingAvailable(STORE_ID, PROVIDER);

        // then
        assertTrue(available);
    }

    @Test
    void refusesOrderingWhenTheGlobalSecretCarriesNoOrderingCredentials() {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.supportsOrdering()).thenReturn(false);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void refusesOrderingWhenNoGlobalSecretExists() {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(null);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void refusesOrderingForManualConnections() {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(null);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void validationMarksFullyAvailableWhenAllQuantitiesCovered() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertTrue(validation.fullyAvailable());
        assertEquals(1, validation.lines().size());
        PurchaseValidation.Line line = validation.lines().get(0);
        assertEquals(5, line.requestedQty());
        assertEquals(10, line.availableQty());
        assertEquals(100.0, line.feedUnitCost());
        assertEquals(110.0, line.liveUnitCost());
        assertEquals(10.0, line.getPriceDelta(), 0.01);
        assertEquals(5 * 110.0, validation.totalNet(), 0.01);
        assertEquals("PLN", validation.currency());
        assertNotNull(validation.purchaseRef());
    }

    @Test
    void validationMarksMissingQuantities() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 3, 100.0, "PLN")));

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertFalse(validation.fullyAvailable());
        assertEquals(2, validation.lines().get(0).getMissingQty());
    }

    @Test
    void validationTreatsMissingQuoteAsUnavailable() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 2, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(List.of());

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertFalse(validation.fullyAvailable());
        assertEquals(0, validation.lines().get(0).availableQty());
    }

    @Test
    void validationSkipsZeroQuantityItems() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 0, 100.0);

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertTrue(validation.lines().isEmpty());
        assertFalse(validation.fullyAvailable());
    }

    @Test
    void processPendingPlacesOrderAndCompletesDelivery() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        verify(supplierProvider).placeOrder(argThat(request -> request.clientOrderRef().equals("ref-1")));
        verify(deliveryCreationService).completePending(eq(STORE_ID), same(delivery), any());
        assertTrue(delivery.hasEvent("DELIVERY_ORDERED_AUTOMATICALLY"));
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        verifyNoInteractions(orderIdRefreshEventPublisher);
    }

    @Test
    void processPendingSchedulesAnOrderIdRefreshWhenTheOrderIsProvisional() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")), true));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        verify(orderIdRefreshEventPublisher).publish(argThat(request ->
                request.getStoreId().equals(STORE_ID)
                        && request.getDeliveryId().equals(DELIVERY_ID)
                        && request.getProvider().equals(PROVIDER)
                        && request.getPurchaseRef().equals("ref-1")));
        assertTrue(delivery.isExternalDeliveryIdProvisional());
    }

    @Test
    void processPendingConvertsConfirmedPricesToLocalCurrency() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 90.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("EUR", 4.0));
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 20.0, "EUR")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 125.0, "EUR",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 25.0, "EUR"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.FlatRate(400, 50)))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        ArgumentCaptor<DeliveryCreationForm> completed = ArgumentCaptor.forClass(DeliveryCreationForm.class);
        verify(deliveryCreationService).completePending(eq(STORE_ID), same(delivery), completed.capture());
        assertEquals(100.0, completed.getValue().getItems().get(0).getUnitCost());
        assertEquals(ExchangeRates.LOCAL_CURRENCY, completed.getValue().getSourceCurrency());
        assertEquals(0.0, completed.getValue().getShippingCost());
    }

    @Test
    void processPendingRebuildsOrderLinesFromClaimedAllocations() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery pending = pendingDelivery(form, "ref-1");
        pending.setDeliveryAddressId("addr-7");

        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, "item-1", "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setName("Product EAN-1");
        allocation.setEan("EAN-1");
        allocation.setMfn("MFN-1");
        allocation.setUnitCost(100.0);
        allocation.setQty(2);

        Delivery withAllocations = new Delivery();
        withAllocations.setDeliveryId(DELIVERY_ID);
        withAllocations.setProvider(PROVIDER);
        withAllocations.setItems(DeliveryItem.groupAndUnify(List.of(allocation)));

        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(pending);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(withAllocations);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        ArgumentCaptor<SupplierPurchaseRequest> request = ArgumentCaptor.forClass(SupplierPurchaseRequest.class);
        verify(supplierProvider).placeOrder(request.capture());
        assertEquals(1, request.getValue().lines().size());
        assertEquals(2, request.getValue().lines().getFirst().quantity());
        assertEquals("addr-7", request.getValue().deliveryAddressId());
    }

    @Test
    void processPendingMarksDeliveryFailedOnSupplierOrderException() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any()))
                .thenThrow(new SupplierOrderException("No Elko code found for EAN 4006381333931"));

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        verify(deliveryCreationService, never()).releaseAllocations(any(), any());
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("No Elko code found for EAN 4006381333931", delivery.getOrderErrorMessage());
        verify(deliveriesRepository).save(delivery);
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    @Test
    void processPendingSkipsDeliveryNotPending() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        verify(storeSupplierProviderResolver, never()).resolve(any(), any());
    }

    @Test
    void processPendingThrowsWhenDeliveryNotFound() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(null);

        // when / then
        assertThrows(IllegalStateException.class, () -> service.processPending(STORE_ID, DELIVERY_ID));
        verify(storeSupplierProviderResolver, never()).resolve(any(), any());
    }

    @Test
    void processPendingFailsDeliveryWhenNoOrderableLines() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 0, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("No orderable lines in pending purchase", delivery.getOrderErrorMessage());
        verify(supplierProvider, never()).placeOrder(any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    @Test
    void processPendingRethrowsUnexpectedExceptions() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        doThrow(new RuntimeException("ddb throttled"))
                .when(deliveryCreationService).completePending(eq(STORE_ID), same(delivery), any());

        // when / then
        assertThrows(RuntimeException.class, () -> service.processPending(STORE_ID, DELIVERY_ID));
    }

    @Test
    void processPendingFailsDeliveryWhenSupplierReturnsBlankOrderId() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "", 180.0, "PLN", List.of()));

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("Supplier confirmed the order without an order number - check the supplier panel before ordering again",
                delivery.getOrderErrorMessage());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    @Test
    void validateEnrichesLinesWithSkuFromResolver() {
        // given
        when(supplierSkuResolver.forStore(STORE_ID, PROVIDER)).thenReturn((ean, mfn) -> "101");
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("4006381333931", "MFN-A", 10, 110.0, "PLN")));

        // when
        service.validate(STORE_ID, form);

        // then
        ArgumentCaptor<List<SupplierOrderLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(supplierProvider).checkAvailability(captor.capture());
        assertEquals("101", captor.getValue().getFirst().sku());
    }

    @Test
    void processPendingCarriesTheChosenDeliveryAddressThroughTheQueue() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        delivery.setDeliveryAddressId("17200617");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("4006381333931", "MFN-A", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "PO-1", 220.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        ArgumentCaptor<SupplierPurchaseRequest> captor = ArgumentCaptor.forClass(SupplierPurchaseRequest.class);
        verify(supplierProvider).placeOrder(captor.capture());
        assertEquals("17200617", captor.getValue().deliveryAddressId());
    }

    @Test
    void processPendingPassesSkuThroughToPlaceOrder() throws Exception {
        // given
        when(supplierSkuResolver.forStore(STORE_ID, PROVIDER)).thenReturn((ean, mfn) -> "101");
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("4006381333931", "MFN-A", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "PO-1", 220.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        ArgumentCaptor<SupplierPurchaseRequest> captor = ArgumentCaptor.forClass(SupplierPurchaseRequest.class);
        verify(supplierProvider).placeOrder(captor.capture());
        assertEquals("101", captor.getValue().lines().getFirst().sku());
    }

    @Test
    void globalSubmissionAwaitsApprovalAndPublishesNothing() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(deliveriesRepository.findByPurchaseRef(eq(STORE_ID), anyString())).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithOneOrderableItem();

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase(STORE_ID, form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        assertTrue(result.getPayload().awaitingApproval());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, saved.getValue().getOrderStatus());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void ownSubmissionGoesStraightToTheQueue() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.OWN);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(supplierProvider);
        when(deliveriesRepository.findByPurchaseRef(eq(STORE_ID), anyString())).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithOneOrderableItem();

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase(STORE_ID, form, "ref-2");

        // then
        assertTrue(result.isSuccess());
        assertFalse(result.getPayload().awaitingApproval());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, saved.getValue().getOrderStatus());
        verify(supplierPurchaseEventPublisher).publish(any(SupplierPurchaseEventRequest.class));
    }

    @Test
    void globalSubmissionDoesNotRequireADeliveryAddressUpFront() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(deliveriesRepository.findByPurchaseRef(eq(STORE_ID), anyString())).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithOneOrderableItem();
        form.setDeliveryAddressId(null);

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase(STORE_ID, form, "ref-3");

        // then
        assertTrue(result.isSuccess());
        verify(globalSupplierProvider, never()).requiresDeliveryAddress();
    }

    @Test
    void submitPurchaseCreatesPendingDeliveryAndPublishes() throws Exception {
        // given
        when(deliveriesRepository.findByPurchaseRef("store-1", "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        form.setProvider("Acme");
        form.setEstimatedDeliveryAt(LocalDate.now().plusDays(3));

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase("store-1", form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, saved.getValue().getOrderStatus());
        assertEquals("ref-1", saved.getValue().getPurchaseRef());
        verify(supplierPurchaseEventPublisher).publish(argThat(request ->
                request.getPurchaseRef().equals("ref-1")
                        && request.getDeliveryId().equals(saved.getValue().getDeliveryId())));
        assertEquals(saved.getValue().getDeliveryId(), result.getPayload().deliveryId());
    }

    @Test
    void submitPurchaseStoresDeliveryAddressIdOnDelivery() {
        // given
        when(deliveriesRepository.findByPurchaseRef("store-1", "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        form.setProvider("Acme");
        form.setDeliveryAddressId("addr-7");

        // when
        service.submitPurchase("store-1", form, "ref-1");

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals("addr-7", saved.getValue().getDeliveryAddressId());
    }

    @Test
    void submitPurchaseRejectsFormWithNoOrderableItems() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 0, 100.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase("store-1", form, "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.availability", result.getMessage());
        verify(deliveriesRepository, never()).save(any(Delivery.class));
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void submitPurchaseFailsClosedWhenTheStoreCannotBeResolved() {
        // given
        when(storesRepository.findById("missing-store")).thenReturn(null);
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase("missing-store", form, "ref-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.failed", result.getMessage());
        verify(deliveriesRepository, never()).save(any(Delivery.class));
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void submitPurchaseIsIdempotentPerPurchaseRef() {
        // given
        Delivery existing = new Delivery();
        existing.setDeliveryId("delivery-1");
        when(deliveriesRepository.findByPurchaseRef("store-1", "ref-1")).thenReturn(Optional.of(existing));
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        form.setProvider("Acme");

        // when
        OperationResult<PurchaseSubmission> result = service.submitPurchase("store-1", form, "ref-1");

        // then
        assertTrue(result.isSuccess());
        assertEquals("delivery-1", result.getPayload().deliveryId());
        verify(deliveriesRepository, never()).save(any(Delivery.class));
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void processPendingRecordsTheResolvedAddressLabelWhenTheSupplierListsTheChosenAddress() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        delivery.setDeliveryAddressId("17200617");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.deliveryAddresses()).thenReturn(List.of(
                new SupplierDeliveryAddress("17200617", "ul. Łobzowska 22/1", "Kraków", "31-140", "PL")));
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals("ul. Łobzowska 22/1, 31-140 Kraków, PL", delivery.getDeliveryAddress());
    }

    @Test
    void processPendingFallsBackToTheRawIdWhenTheSupplierNoLongerListsTheChosenAddress() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        delivery.setDeliveryAddressId("99999999");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.deliveryAddresses()).thenReturn(List.of(
                new SupplierDeliveryAddress("17200617", "ul. Łobzowska 22/1", "Kraków", "31-140", "PL")));
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals("99999999", delivery.getDeliveryAddress());
    }

    @Test
    void processPendingFallsBackToTheRawIdWhenAddressLookupThrowsAndTheOrderStillCompletes() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        delivery.setDeliveryAddressId("17200617");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.deliveryAddresses()).thenThrow(new RuntimeException("503 Service Unavailable"));
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals("17200617", delivery.getDeliveryAddress());
        verify(deliveryCreationService).completePending(eq(STORE_ID), same(delivery), any());
    }

    @Test
    void processPendingRecordsNothingWhenNoAddressWasChosen() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "555", 180.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertNull(delivery.getDeliveryAddress());
    }

    @Test
    void processPendingRecordsTheAttemptedAddressEvenWhenThePurchaseFails() throws Exception {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = pendingDelivery(form, "ref-1");
        delivery.setDeliveryAddressId("17200617");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(supplierProvider.deliveryAddresses()).thenReturn(List.of(
                new SupplierDeliveryAddress("17200617", "ul. Łobzowska 22/1", "Kraków", "31-140", "PL")));
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any()))
                .thenThrow(new SupplierOrderException("No Elko code found for EAN 4006381333931"));

        // when
        service.processPending(STORE_ID, DELIVERY_ID);

        // then
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("ul. Łobzowska 22/1, 31-140 Kraków, PL", delivery.getDeliveryAddress());
    }

    private Delivery pendingDelivery(DeliveryCreationForm form, String purchaseRef) throws Exception {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        delivery.setProvider(form.getProvider());
        lenient().when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID))
                .thenReturn(deliveryWithAllocations(form, DELIVERY_ID));
        return delivery;
    }

    private Delivery deliveryWithAllocations(DeliveryCreationForm form, String deliveryId) {
        List<Allocation> allocations = form.getItems().stream()
                .map(item -> {
                    Allocation allocation = new Allocation();
                    allocation.setKey(new AllocationKey(null, java.util.UUID.randomUUID().toString(), "Warehouse"));
                    allocation.setType(AllocationType.Warehouse);
                    allocation.setName(item.getName());
                    allocation.setEan(item.getEan());
                    allocation.setMfn(item.getMfn());
                    allocation.setUnitCost(item.getUnitCost());
                    allocation.setQty(item.getRequestedQty());
                    return allocation;
                })
                .toList();
        Delivery withAllocations = new Delivery();
        withAllocations.setDeliveryId(deliveryId);
        withAllocations.setProvider(form.getProvider());
        withAllocations.setItems(DeliveryItem.groupAndUnify(allocations));
        return withAllocations;
    }

    @Test
    void validateConvertsLiveQuoteToLocalCurrencyUsingSellRate() {
        // given
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("EUR", 4.34));
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 434.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("4006381333931", "MFN-A", 10, 100.0, "EUR")));

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertEquals("PLN", validation.currency());
        assertEquals(434.0, validation.lines().getFirst().liveUnitCost(), 0.001);
        assertEquals(0.0, validation.lines().getFirst().getPriceDelta(), 0.001);
        assertEquals(868.0, validation.totalNet(), 0.001);
    }

    @Test
    void validateKeepsSupplierCurrencyWhenSellRateUnavailable() {
        // given
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 434.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("4006381333931", "MFN-A", 10, 100.0, "EUR")));

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertEquals("EUR", validation.currency());
        assertEquals(100.0, validation.lines().getFirst().liveUnitCost(), 0.001);
    }

    @Test
    void validateDoesNotTouchExchangeRatesForLocalCurrencyQuotes() {
        // given
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("4006381333931", "MFN-A", 10, 110.0, "PLN")));

        // when
        PurchaseValidation validation = service.validate(STORE_ID, form);

        // then
        assertEquals("PLN", validation.currency());
        assertEquals(110.0, validation.lines().getFirst().liveUnitCost(), 0.001);
        verifyNoInteractions(exchangeRates);
    }

    @Test
    void doesNotExposeAManagedFlagOnDeliveries() {
        // given / when / then
        assertTrue(java.util.Arrays.stream(Delivery.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("isManaged")
                        || method.getName().equals("setManaged")));
    }

    @Test
    void approveStoresChosenDeliveryAddressIdOnDelivery() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.requiresDeliveryAddress()).thenReturn(true);
        when(globalSupplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("5900000000001", "MFN-1", 5, 9.5, "PLN")));
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        service.approve(STORE_ID, DELIVERY_ID, "addr-9");

        // then
        assertEquals("addr-9", delivery.getDeliveryAddressId());
    }

    @Test
    void approvalIsRefusedWhenTheSupplierRequiresAnAddressAndNoneWasChosen() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.requiresDeliveryAddress()).thenReturn(true);
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "  ");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.address", result.getMessage());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, delivery.getOrderStatus());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void approvalIsRefusedForADeliveryThatIsNotAwaitingApproval() throws Exception {
        // given
        Delivery delivery = awaitingApprovalDelivery();
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertFalse(result.isSuccess());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void approvalIsRefusedWhenAvailabilityHasBecomePartial() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("5900000000001", "MFN-1", 0, 9.5, "PLN")));
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.availability", result.getMessage());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, delivery.getOrderStatus());
        verify(deliveriesRepository, never()).save(any());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void approvalSucceedsWhenFullyAvailable() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("5900000000001", "MFN-1", 5, 9.5, "PLN")));
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertTrue(result.isSuccess());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, delivery.getOrderStatus());
        verify(supplierPurchaseEventPublisher).publish(any(SupplierPurchaseEventRequest.class));
    }

    @Test
    void approvalFailsClosedWhenTheAvailabilityRecheckThrows() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        when(globalSupplierProvider.checkAvailability(anyList()))
                .thenThrow(new SupplierOrderException("503 Service Unavailable"));
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.availability", result.getMessage());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, delivery.getOrderStatus());
        verify(deliveriesRepository, never()).save(any());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void approvalIsRefusedWhenTheStoreHasSwitchedTheConnectionToOwnSinceSubmission() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.OWN);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.approval.error.state", result.getMessage());
        assertEquals(DeliveryOrderStatus.AWAITING_APPROVAL, delivery.getOrderStatus());
        verify(deliveriesRepository, never()).save(any());
        verifyNoInteractions(supplierPurchaseEventPublisher);
        verifyNoInteractions(storeSupplierProviderResolver);
    }

    @Test
    void globalSubmissionClaimsItsAllocationsSoTheyCannotBeOrderedTwice() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(deliveriesRepository.findByPurchaseRef(eq(STORE_ID), anyString())).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithOneOrderableItem();

        // when
        service.submitPurchase(STORE_ID, form, "ref-claim");

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        verify(deliveryCreationService).claimAllocations(STORE_ID, saved.getValue(), form);
    }

    @Test
    void rejectionReturnsTheAllocationsToTheSupplier() throws Exception {
        // given
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        service.reject(STORE_ID, DELIVERY_ID, "Cena wzrosla o 20%");

        // then
        verify(deliveryCreationService).releaseAllocations(STORE_ID, delivery);
    }

    @Test
    void rejectReleasesAllocationsAndDeletesTheDelivery() throws Exception {
        // given
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.reject(STORE_ID, DELIVERY_ID, "Cena wzrosla o 20%");

        // then
        assertTrue(result.isSuccess());
        verify(deliveryCreationService).releaseAllocations(STORE_ID, delivery);
        verify(deliveriesRepository).delete(delivery);
        verify(deliveriesRepository, never()).save(delivery);
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void rejectionIsRefusedWhenTheDeliveryHasAlreadyBeenReceived() throws Exception {
        // given
        Delivery delivery = awaitingApprovalDelivery();
        delivery.setReceivedAt(java.time.LocalDateTime.now());
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.reject(STORE_ID, DELIVERY_ID, "Cena wzrosla o 20%");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.approval.error.state", result.getMessage());
        verify(deliveryCreationService, never()).releaseAllocations(any(), any());
        verify(deliveriesRepository, never()).delete(any(Delivery.class));
    }

    @Test
    void approvalIsRefusedWhenTheDeliveryHasAlreadyBeenReceived() throws Exception {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        Delivery delivery = awaitingApprovalDelivery();
        delivery.setReceivedAt(java.time.LocalDateTime.now());
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.approval.error.state", result.getMessage());
        verify(deliveriesRepository, never()).save(any(Delivery.class));
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void approvalIsRefusedWhenTheDeliveryDoesNotExist() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(null);

        // when
        OperationResult<String> result = service.approve(STORE_ID, DELIVERY_ID, "17200617");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.approval.error.state", result.getMessage());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void rejectionIsRefusedWhenTheDeliveryDoesNotExist() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(null);

        // when
        OperationResult<String> result = service.reject(STORE_ID, DELIVERY_ID, "Cena wzrosla o 20%");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.approval.error.state", result.getMessage());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void revalidatesThePendingFormAgainstTheSupplierAtApprovalTime() throws Exception {
        // given
        when(storeSupplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(globalSupplierProvider);
        Delivery delivery = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierSkuResolver.forStore(STORE_ID, PROVIDER))
                .thenReturn((ean, mfn) -> "SKU-1");
        when(globalSupplierProvider.checkAvailability(anyList()))
                .thenReturn(List.of(new SupplierQuote("5900000000001", "MFN-1", 5, 9.5, "PLN")));

        // when
        PurchaseValidation validation = service.validatePending(STORE_ID, DELIVERY_ID);

        // then
        assertTrue(validation.fullyAvailable());
        assertEquals(1, validation.lines().size());
        assertEquals(9.5, validation.lines().get(0).liveUnitCost());
        verify(globalSupplierProvider).checkAvailability(anyList());
    }

    private Delivery awaitingApprovalDelivery() throws Exception {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        delivery.setPurchaseRef("ref-1");
        lenient().when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID))
                .thenReturn(deliveryWithAllocations(formWithOneOrderableItem(), DELIVERY_ID));
        return delivery;
    }

    private DeliveryCreationForm formWithOneOrderableItem() {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(STORE_ID);
        form.setProvider(PROVIDER);
        DeliveryItem item = new DeliveryItem();
        item.setEan("5900000000001");
        item.setMfn("MFN-1");
        item.setName("Item");
        item.setRequestedQty(1);
        item.setUnitCost(10.0);
        form.getItems().add(item);
        return form;
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

    @Test
    void retryRepublishesFailedPurchaseWithSamePurchaseRef() {
        // given
        Delivery failed = new Delivery();
        failed.setDeliveryId(DELIVERY_ID);
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);
        failed.setOrderErrorMessage("boom");
        failed.setProvider(PROVIDER);
        failed.setPurchaseRef("ref-1");
        when(deliveriesRepository.findById(STORE_ID, failed.getDeliveryId())).thenReturn(failed);

        // when
        OperationResult<String> result = service.retry(STORE_ID, failed.getDeliveryId());

        // then
        assertTrue(result.isSuccess());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, failed.getOrderStatus());
        assertNull(failed.getOrderErrorMessage());
        verify(deliveriesRepository).save(failed);
        ArgumentCaptor<SupplierPurchaseEventRequest> event = ArgumentCaptor.forClass(SupplierPurchaseEventRequest.class);
        verify(supplierPurchaseEventPublisher).publish(event.capture());
        assertEquals("ref-1", event.getValue().getPurchaseRef());
        assertEquals(failed.getDeliveryId(), event.getValue().getDeliveryId());
    }

    @Test
    void retryRefusesDeliveryThatDidNotFail() {
        // given
        Delivery pending = new Delivery();
        pending.setDeliveryId(DELIVERY_ID);
        pending.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesRepository.findById(STORE_ID, pending.getDeliveryId())).thenReturn(pending);

        // when
        OperationResult<String> result = service.retry(STORE_ID, pending.getDeliveryId());

        // then
        assertFalse(result.isSuccess());
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void retryRefusesDeliveryThatHasAlreadyBeenReceived() {
        // given
        Delivery failed = new Delivery();
        failed.setDeliveryId(DELIVERY_ID);
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);
        failed.setReceivedAt(java.time.LocalDateTime.now());
        when(deliveriesRepository.findById(STORE_ID, failed.getDeliveryId())).thenReturn(failed);

        // when
        OperationResult<String> result = service.retry(STORE_ID, failed.getDeliveryId());

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.retry.error.state", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void retryRefusesWhenTheDeliveryDoesNotExist() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(null);

        // when
        OperationResult<String> result = service.retry(STORE_ID, DELIVERY_ID);

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.retry.error.state", result.getMessage());
        verifyNoInteractions(supplierPurchaseEventPublisher);
    }

    @Test
    void completeManuallyClearsFailedStateAndSetsOrderNumber() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = failedDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        OperationResult<String> result = service.completeManually(STORE_ID, DELIVERY_ID, "PO-999");

        // then
        assertTrue(result.isSuccess());
        assertEquals(DELIVERY_ID, result.getPayload());
        ArgumentCaptor<DeliveryCreationForm> captor = ArgumentCaptor.forClass(DeliveryCreationForm.class);
        verify(deliveryCreationService).completePending(eq(STORE_ID), same(delivery), captor.capture());
        assertEquals("PO-999", captor.getValue().getExternalDeliveryId());
        assertEquals(ExchangeRates.LOCAL_CURRENCY, captor.getValue().getSourceCurrency());
        assertNull(delivery.getOrderErrorMessage());
        assertTrue(delivery.hasEvent("DELIVERY_ORDERED_MANUALLY"));
    }

    @Test
    void completeManuallyAppliesShippingTermsAndTax() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = failedDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(3, new ShippingCostPolicy.FlatRate(1000, 50)))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.0);

        // when
        service.completeManually(STORE_ID, DELIVERY_ID, "PO-1");

        // then
        ArgumentCaptor<DeliveryCreationForm> captor = ArgumentCaptor.forClass(DeliveryCreationForm.class);
        verify(deliveryCreationService).completePending(eq(STORE_ID), same(delivery), captor.capture());
        assertEquals(LocalDate.now().plusDays(3), captor.getValue().getEstimatedDeliveryAt());
        assertEquals(50.0, captor.getValue().getShippingCost());
        assertEquals(1.0, captor.getValue().getTax());
    }

    @Test
    void completeManuallySetsProvisionalFalseAndKeepsPurchaseRef() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = failedDelivery(form, "ref-1");
        delivery.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.completeManually(STORE_ID, DELIVERY_ID, "PO-1");

        // then
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        assertEquals("ref-1", delivery.getPurchaseRef());
    }

    @Test
    void completeManuallyDoesNotCallSupplierProvider() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        Delivery delivery = failedDelivery(form, "ref-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);

        // when
        service.completeManually(STORE_ID, DELIVERY_ID, "PO-1");

        // then
        verifyNoInteractions(storeSupplierProviderResolver);
        verifyNoInteractions(supplierProvider);
    }

    @Test
    void completeManuallyRejectsNonFailedDelivery() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.completeManually(STORE_ID, DELIVERY_ID, "PO-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.complete.error.state", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    @Test
    void completeManuallyRejectsReceivedDelivery() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setReceivedAt(java.time.LocalDateTime.now());
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.completeManually(STORE_ID, DELIVERY_ID, "PO-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.complete.error.state", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    @Test
    void completeManuallyRejectsDeliveryWithDocuments() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.getDocuments().add(new pl.commercelink.documents.Document());
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.completeManually(STORE_ID, DELIVERY_ID, "PO-1");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.complete.error.state", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    @Test
    void completeManuallyRejectsBlankOrderNumber() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<String> result = service.completeManually(STORE_ID, DELIVERY_ID, "   ");

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.complete.error.number", result.getMessage());
        verify(deliveriesRepository, never()).save(any());
        verify(deliveryCreationService, never()).completePending(any(), any(), any());
    }

    private Delivery failedDelivery(DeliveryCreationForm form, String purchaseRef) {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setOrderErrorMessage("boom");
        delivery.setPurchaseRef(purchaseRef);
        delivery.setProvider(form.getProvider());
        lenient().when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID))
                .thenReturn(deliveryWithAllocations(form, DELIVERY_ID));
        return delivery;
    }

    @Test
    void stampsTheGlobalConnectionModeOnADeliveryRaisedForApproval() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(deliveriesRepository.findByPurchaseRef(STORE_ID, "ref-1")).thenReturn(Optional.empty());
        when(supplierConnectionModeResolver.resolve(store, PROVIDER)).thenReturn(ConnectionMode.GLOBAL);
        DeliveryCreationForm form = formWithOneOrderableItem();

        // when
        service.submitPurchase(STORE_ID, form, "ref-1");

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(ConnectionMode.GLOBAL, saved.getValue().getConnectionMode());
    }
}
