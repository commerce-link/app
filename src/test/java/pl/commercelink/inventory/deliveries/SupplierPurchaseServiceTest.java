package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
