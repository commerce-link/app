package pl.commercelink.inventory.supplier.manual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.supplier.StoreFeedRepository;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualSupplierServiceTest {

    @Mock StoresRepository storesRepository;
    @Mock StoreFeedRepository storeFeedRepository;
    @Mock StoreInventoryCache storeInventoryCache;
    @Mock SupplierRegistry supplierRegistry;
    @InjectMocks ManualSupplierService service;

    private Store storeWith(StoreSupplierConnection... connections) {
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new java.util.ArrayList<>(List.of(connections)));
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void createAddsManualConnection() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        ManualSupplierService.Result result = service.create("store-1", "Hurtownia Ą");

        // then
        assertTrue(result.ok());
        assertTrue(result.identity().matches("^manual-[a-z0-9]{8}$"));
        StoreSupplierConnection created = store.getSupplierConnections().get(0);
        assertEquals(result.identity(), created.getSupplierName());
        assertEquals("Hurtownia Ą", created.getLabel());
        assertEquals(ConnectionMode.MANUAL, created.getMode());
        assertFalse(created.isEnabled());
        verify(storesRepository).save(store);
        verify(storeInventoryCache).evict("store-1");
    }

    @Test
    void createRejectsALabelAlreadyUsedByAnyConnection() {
        // given
        StoreSupplierConnection legacy = new StoreSupplierConnection("manual:Asus", ConnectionMode.MANUAL, true, true);
        Store store = storeWith(legacy, new StoreSupplierConnection("Kosatec", ConnectionMode.OWN, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when / then
        assertEquals("store.manual.error.name.taken", service.create("store-1", "asus").messageCode());
        assertEquals("store.manual.error.name.taken", service.create("store-1", "kosatec").messageCode());
    }

    @Test
    void createRefusesABuiltInOrTypeName() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(storeWith());
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Amazon", "Warehouse", "Other", "Kosatec"));

        // when / then
        assertEquals("store.supplier.connection.error.label.reserved", service.create("store-1", "Warehouse").messageCode());
        assertEquals("store.supplier.connection.error.label.reserved", service.create("store-1", "kosatec").messageCode());
    }

    @Test
    void renameRefusesABuiltInOrTypeName() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual-k7f3a9c2", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Amazon", "Warehouse", "Other", "Kosatec"));

        // when
        ManualSupplierService.Result result = service.applySelections("store-1", List.of(
                new ManualSupplierService.ManualSelection("manual-k7f3a9c2", true, true, true, null, "warehouse")));

        // then
        assertEquals("store.supplier.connection.error.label.reserved", result.messageCode());
        verify(storesRepository, never()).save(any());
    }

    @Test
    void createRejectsBlankAndOverlongLabels() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(storeWith());

        // when / then
        assertEquals("store.manual.error.name.invalid", service.create("store-1", "  ").messageCode());
        assertEquals("store.manual.error.name.invalid", service.create("store-1", "x".repeat(61)).messageCode());
    }

    @Test
    void deleteReturnsSuccessResultWhenRemoved() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL));
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        ManualSupplierService.Result result = service.delete("store-1", "manual:Hurtownia A");

        // then
        assertTrue(result.ok());
    }

    @Test
    void deleteReturnsNotFoundResultWhenIdentityUnknown() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL));
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        ManualSupplierService.Result result = service.delete("store-1", "manual:Nope");

        // then
        assertFalse(result.ok());
        assertEquals("store.manual.error.supplier.notfound", result.messageCode());
        verify(storeFeedRepository, never()).delete(anyString(), anyString());
    }

    @Test
    void deleteRemovesConnectionAndFile() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL));
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        service.delete("store-1", "manual:Hurtownia A");

        // then
        assertFalse(store.getManualSupplierNames().contains("manual:Hurtownia A"));
        verify(storeFeedRepository).delete("store-1", "manual:Hurtownia A");
        verify(storesRepository).save(store);
    }

    @Test
    void deleteWithNonManualIdentityDoesNotDeleteFeedOrSave() {
        // given
        Store store = storeWith(new StoreSupplierConnection("Action", ConnectionMode.OWN));
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        service.delete("store-1", "Action");

        // then
        verify(storeFeedRepository, never()).delete(anyString(), anyString());
        verify(storesRepository, never()).save(any());
    }

    @Test
    void applySelectionsUpdatesEnabledAndFlags() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        lenient().when(storeFeedRepository.canRead("store-1", "manual:Hurtownia A", "csv")).thenReturn(true);
        ManualSupplierService.ManualSelection selection =
                new ManualSupplierService.ManualSelection("manual:Hurtownia A", false, false, true, " 2 ", null);

        // when
        service.applySelections("store-1", List.of(selection));

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertFalse(connection.isEnabled());
        assertFalse(connection.isIncludeInPricing());
        assertTrue(connection.isIncludeInFulfilment());
        assertEquals("2", connection.getExternalSupplierId());
        verify(storesRepository).save(store);
    }

    @Test
    void applySelectionsForcesEnabledFalseWhenNoFeed() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeFeedRepository.canRead("store-1", "manual:Hurtownia A", "csv")).thenReturn(false);
        ManualSupplierService.ManualSelection selection =
                new ManualSupplierService.ManualSelection("manual:Hurtownia A", true, true, true, null, null);

        // when
        service.applySelections("store-1", List.of(selection));

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertFalse(connection.isEnabled());
        verify(storesRepository).save(store);
    }

    @Test
    void applySelectionsKeepsEnabledTrueWhenFeedPresent() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeFeedRepository.canRead("store-1", "manual:Hurtownia A", "csv")).thenReturn(true);
        ManualSupplierService.ManualSelection selection =
                new ManualSupplierService.ManualSelection("manual:Hurtownia A", true, true, true, null, null);

        // when
        service.applySelections("store-1", List.of(selection));

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertTrue(connection.isEnabled());
        verify(storesRepository).save(store);
    }

    @Test
    void applySelectionsRenamesTheConnection() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("manual-k7f3a9c2", ConnectionMode.MANUAL, true, true);
        connection.setLabel("Stara");
        Store store = storeWith(connection);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeFeedRepository.canRead("store-1", "manual-k7f3a9c2", "csv")).thenReturn(true);

        // when
        service.applySelections("store-1", List.of(
                new ManualSupplierService.ManualSelection("manual-k7f3a9c2", true, true, true, null, "Nowa")));

        // then
        assertEquals("Nowa", connection.getLabel());
    }

    @Test
    void createFailsExplicitlyWhenEveryGeneratedIdentityCollides() {
        // given -- a generator stuck on one token, so all five attempts hit the existing connection
        Store store = storeWith(new StoreSupplierConnection("manual-aaaaaaaa", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        ManualSupplierService colliding = new ManualSupplierService(
                storesRepository, storeFeedRepository, storeInventoryCache, supplierRegistry) {
            @Override
            String newIdentity() {
                return "manual-aaaaaaaa";
            }
        };

        // when
        ManualSupplierService.Result result = colliding.create("store-1", "Hurtownia B");

        // then
        assertFalse(result.ok());
        assertEquals("store.supplier.connection.error.identity.exhausted", result.messageCode());
        verify(storesRepository, never()).save(store);
    }

    @Test
    void applySelectionsRejectsAnInvalidLabelInsteadOfDroppingIt() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("manual-k7f3a9c2", ConnectionMode.MANUAL, true, true);
        connection.setLabel("Stara");
        Store store = storeWith(connection);
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        ManualSupplierService.Result result = service.applySelections("store-1", List.of(
                new ManualSupplierService.ManualSelection("manual-k7f3a9c2", true, true, true, null, "   ")));

        // then -- nothing is written and the caller learns why
        assertFalse(result.ok());
        assertEquals("store.manual.error.name.invalid", result.messageCode());
        assertEquals("Stara", connection.getLabel());
        verify(storesRepository, never()).save(store);
    }

    @Test
    void applySelectionsRejectsALabelAnotherConnectionAlreadyUses() {
        // given
        StoreSupplierConnection renamed = new StoreSupplierConnection("manual-k7f3a9c2", ConnectionMode.MANUAL, true, true);
        renamed.setLabel("Stara");
        StoreSupplierConnection other = new StoreSupplierConnection("manual-9x2pq0ab", ConnectionMode.MANUAL, true, true);
        other.setLabel("Hurtownia A");
        Store store = storeWith(renamed, other);
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        ManualSupplierService.Result result = service.applySelections("store-1", List.of(
                new ManualSupplierService.ManualSelection("manual-k7f3a9c2", true, true, true, null, "hurtownia a")));

        // then
        assertFalse(result.ok());
        assertEquals("store.manual.error.name.taken", result.messageCode());
        assertEquals("Stara", renamed.getLabel());
        verify(storesRepository, never()).save(store);
    }

    @Test
    void uploadFeedStoresValidCsv() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        String csv = "ean;mfn;brand;name;category;net_price;currency;qty;lead_time_days\n"
                + "5901234123457;MFN-1;BrandX;Mysz;Mice;12,50;PLN;7;2\n";
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);

        // when
        ManualSupplierService.Result result = service.uploadFeed("store-1", "manual:Hurtownia A", csvBytes);

        // then
        assertTrue(result.ok());
        verify(storeFeedRepository).store(eq("store-1"), eq("manual:Hurtownia A"), any(), eq("csv"));
    }

    @Test
    void uploadFeedRejectsUnloadableCsv() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        // qty=0 is not sellable (isSellable() requires qty>0), so no row is loadable
        String csv = "ean;mfn;brand;name;category;net_price;currency;qty;lead_time_days\n"
                + "5901234123457;MFN-1;BrandX;Mysz;Mice;12,50;PLN;0;2\n";
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);

        // when
        ManualSupplierService.Result result = service.uploadFeed("store-1", "manual:Hurtownia A", csvBytes);

        // then
        assertFalse(result.ok());
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(), anyString());
    }

    @Test
    void createEvictsStoreInventoryCache() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        service.create("store-1", "Hurtownia A");

        // then
        verify(storeInventoryCache).evict("store-1");
    }

    @Test
    void uploadFeedEvictsStoreInventoryCache() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        String csv = "ean;mfn;brand;name;category;net_price;currency;qty;lead_time_days\n"
                + "5901234123457;MFN-1;BrandX;Mysz;Mice;12,50;PLN;7;2\n";

        // when
        service.uploadFeed("store-1", "manual:Hurtownia A", csv.getBytes(StandardCharsets.UTF_8));

        // then
        verify(storeInventoryCache).evict("store-1");
    }

    @Test
    void deleteEvictsStoreInventoryCache() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL));
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        service.delete("store-1", "manual:Hurtownia A");

        // then
        verify(storeInventoryCache).evict("store-1");
    }

    @Test
    void applySelectionsEvictsStoreInventoryCache() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        lenient().when(storeFeedRepository.canRead("store-1", "manual:Hurtownia A", "csv")).thenReturn(true);

        // when
        service.applySelections("store-1",
                List.of(new ManualSupplierService.ManualSelection("manual:Hurtownia A", true, true, true, null, null)));

        // then
        verify(storeInventoryCache).evict("store-1");
    }

    @Test
    void uploadFeedRejectsUnknownIdentity() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);
        byte[] csvBytes = "ean;mfn;brand;name;category;net_price;currency;qty;lead_time_days\n".getBytes(StandardCharsets.UTF_8);

        // when
        ManualSupplierService.Result result = service.uploadFeed("store-1", "manual:Nope", csvBytes);

        // then
        assertFalse(result.ok());
        assertEquals("store.manual.error.supplier.notfound", result.messageCode());
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(), anyString());
    }
}
