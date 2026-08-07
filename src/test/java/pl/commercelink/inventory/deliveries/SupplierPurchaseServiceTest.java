package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";

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
    private pl.commercelink.inventory.supplier.SupplierRegistry supplierRegistry;
    @Mock
    private SupplierProvider supplierProvider;

    @InjectMocks
    private SupplierPurchaseService service;

    private final Store store = new Store();

    @BeforeEach
    void setUp() {
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(supplierProviderFactory.get(store, PROVIDER)).thenReturn(supplierProvider);
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
    }

    @Test
    void purchaseCreatesDeliveryWithSupplierOrderData() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "ACME-PO-ref-1", 550.0, "PLN",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        when(deliveryCreationService.run(eq(STORE_ID), any(), eq(false))).thenReturn("delivery-1");
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(new Delivery());

        // when
        OperationResult<String> result = service.purchase(STORE_ID, form, "ref-1", false);

        // then
        assertTrue(result.isSuccess());
        assertEquals("delivery-1", result.getPayload());
        ArgumentCaptor<SupplierPurchaseRequest> requestCaptor =
                ArgumentCaptor.forClass(SupplierPurchaseRequest.class);
        verify(supplierProvider).placeOrder(requestCaptor.capture());
        assertEquals("ref-1", requestCaptor.getValue().clientOrderRef());
        assertEquals("ACME-PO-ref-1", form.getExternalDeliveryId());
        assertEquals(110.0, form.getItems().get(0).getUnitCost());
        assertEquals(LocalDate.now().plusDays(2), form.getEstimatedDeliveryAt());
        assertEquals(1.23, form.getTax());
        assertEquals(0.0, form.getShippingCost());
        verify(deliveriesRepository).save(any(Delivery.class));
    }

    @Test
    void purchaseAbortsWhenRevalidationFindsShortage() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 3, 100.0, "PLN")));

        // when
        OperationResult<String> result = service.purchase(STORE_ID, form, "ref-1", false);

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.availability", result.getMessage());
        verify(supplierProvider, never()).placeOrder(any());
        verify(deliveryCreationService, never()).run(any(), any(), anyBoolean());
    }

    @Test
    void purchaseFailsGracefullyWhenPlaceOrderThrows() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenThrow(new SupplierOrderException("boom"));

        // when
        OperationResult<String> result = service.purchase(STORE_ID, form, "ref-1", false);

        // then
        assertFalse(result.isSuccess());
        assertEquals("deliveries.purchase.error.failed", result.getMessage());
        verify(deliveryCreationService, never()).run(any(), any(), anyBoolean());
    }

    @Test
    void purchaseAppliesFlatRateShippingBelowThreshold() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 1, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "PO-2", 110.0, "PLN", List.of()));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(3, new ShippingCostPolicy.FlatRate(2000, 20)))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        when(deliveryCreationService.run(eq(STORE_ID), any(), eq(false))).thenReturn("delivery-2");
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(new Delivery());

        // when
        service.purchase(STORE_ID, form, "ref-2", false);

        // then
        assertEquals(20.0, form.getShippingCost());
    }

    @Test
    void purchaseUsesSupplierConfirmedPricesOverPreOrderQuote() {
        // given
        DeliveryCreationForm form = formWithItem("EAN-1", "MFN-1", 5, 100.0);
        when(supplierProvider.checkAvailability(anyList())).thenReturn(
                List.of(new SupplierQuote("EAN-1", "MFN-1", 10, 110.0, "PLN")));
        when(supplierProvider.placeOrder(any())).thenReturn(new SupplierOrderResult(
                "PO-3", 575.0, "PLN",
                List.of(new SupplierQuote("EAN-1", "MFN-1", 5, 115.0, "PLN"))));
        when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
        when(deliveryCreationService.run(eq(STORE_ID), any(), eq(false))).thenReturn("delivery-3");
        when(deliveriesRepository.findById(STORE_ID, "delivery-3")).thenReturn(new Delivery());

        // when
        service.purchase(STORE_ID, form, "ref-3", false);

        // then
        assertEquals(115.0, form.getItems().get(0).getUnitCost());
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
