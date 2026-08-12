package pl.commercelink.inventory.deliveries;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDate;
import java.util.List;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String DELIVERY_ID = "delivery-1";

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
    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @InjectMocks
    private SupplierPurchaseService service;

    private final Store store = new Store();

    @BeforeEach
    void setUp() {
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(supplierProviderFactory.get(store, PROVIDER)).thenReturn(supplierProvider);
        lenient().when(supplierSkuResolver.forStore(anyString(), anyString())).thenReturn((ean, mfn) -> null);
    }

    @Test
    void orderingAvailableWhenProviderSupportsIt() {
        // given
        when(supplierProvider.supportsOrdering()).thenReturn(true);

        // when / then
        assertTrue(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void orderingUnavailableWhenProviderMissing() {
        // given
        when(supplierProviderFactory.get(store, PROVIDER)).thenReturn(null);

        // when / then
        assertFalse(service.isOrderingAvailable(STORE_ID, PROVIDER));
    }

    @Test
    void orderingUnavailableWhenFactoryThrows() {
        // given
        when(supplierProviderFactory.get(store, PROVIDER)).thenThrow(new RuntimeException("no credentials"));

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
        verify(supplierProviderFactory, never()).get(any(), any());
    }

    @Test
    void processPendingThrowsWhenDeliveryNotFound() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(null);

        // when / then
        assertThrows(IllegalStateException.class, () -> service.processPending(STORE_ID, DELIVERY_ID));
        verify(supplierProviderFactory, never()).get(any(), any());
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
    void enqueuePurchaseCreatesPendingDeliveryAndPublishes() throws Exception {
        // given
        when(deliveriesRepository.findByPurchaseRef("store-1", "ref-1")).thenReturn(Optional.empty());
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        form.setProvider("Acme");
        form.setEstimatedDeliveryAt(LocalDate.now().plusDays(3));

        // when
        OperationResult<String> result = service.enqueuePurchase("store-1", form, "ref-1", false);

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertEquals(DeliveryOrderStatus.ORDER_PENDING, saved.getValue().getOrderStatus());
        assertEquals("ref-1", saved.getValue().getPurchaseRef());
        assertNotNull(saved.getValue().getPendingOrderForm());
        verify(supplierPurchaseEventPublisher).publish(argThat(request ->
                request.getPurchaseRef().equals("ref-1")
                        && request.getDeliveryId().equals(saved.getValue().getDeliveryId())));
        assertEquals(saved.getValue().getDeliveryId(), result.getPayload());
        DeliveryCreationForm rehydrated = objectMapper.readValue(
                saved.getValue().getPendingOrderForm(), DeliveryCreationForm.class);
        assertEquals(form.getItems().get(0).getEan(), rehydrated.getItems().get(0).getEan());
        assertEquals(form.getItems().get(0).getRequestedQty(), rehydrated.getItems().get(0).getRequestedQty());
        assertEquals(form.getEstimatedDeliveryAt(), rehydrated.getEstimatedDeliveryAt());
    }

    @Test
    void enqueuePurchaseRejectsFormWithNoOrderableItems() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 0, 100.0);

        // when
        OperationResult<String> result = service.enqueuePurchase("store-1", form, "ref-1", false);

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.availability", result.getMessage());
        verify(deliveriesRepository, never()).save(any(Delivery.class));
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    @Test
    void enqueuePurchaseIsIdempotentPerPurchaseRef() {
        // given
        Delivery existing = new Delivery();
        existing.setDeliveryId("delivery-1");
        when(deliveriesRepository.findByPurchaseRef("store-1", "ref-1")).thenReturn(Optional.of(existing));
        DeliveryCreationForm form = formWithItem("4006381333931", "MFN-A", 2, 90.0);
        form.setProvider("Acme");

        // when
        OperationResult<String> result = service.enqueuePurchase("store-1", form, "ref-1", false);

        // then
        assertTrue(result.isSuccess());
        assertEquals("delivery-1", result.getPayload());
        verify(deliveriesRepository, never()).save(any(Delivery.class));
        verify(supplierPurchaseEventPublisher, never()).publish(any());
    }

    private Delivery pendingDelivery(DeliveryCreationForm form, String purchaseRef) throws Exception {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        delivery.setPendingOrderForm(objectMapper.writeValueAsString(form));
        return delivery;
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
}
