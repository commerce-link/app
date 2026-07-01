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
    @Mock SupplierRegistry supplierRegistry;
    @Mock StoreInventoryCache storeInventoryCache;
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
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Acme"));

        // when
        ManualSupplierService.Result result = service.create("store-1", "Hurtownia A");

        // then
        assertTrue(result.ok());
        assertTrue(store.getManualSupplierNames().contains("manual:Hurtownia A"));
        verify(storesRepository).save(store);
    }

    @Test
    void createStartsSupplierDisabled() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Acme"));

        // when
        service.create("store-1", "Hurtownia A");

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertFalse(connection.isEnabled());
    }

    @Test
    void createProducesConsistentManualNaming() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Acme"));

        // when
        service.create("store-1", "Hurtownia A");

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertEquals(ConnectionMode.MANUAL, connection.getMode());
        assertEquals("manual:Hurtownia A", connection.getSupplierName());
    }

    @Test
    void createRejectsDuplicateLabel() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Acme"));

        // when
        ManualSupplierService.Result result = service.create("store-1", "hurtownia a");

        // then
        assertFalse(result.ok());
    }

    @Test
    void createRejectsCollisionWithStaticSupplier() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Acme"));

        // when
        ManualSupplierService.Result result = service.create("store-1", "Acme");

        // then
        assertFalse(result.ok());
    }

    @Test
    void createRejectsInvalidCharset() {
        // given
        Store store = storeWith();
        when(storesRepository.findById("store-1")).thenReturn(store);
        lenient().when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of());

        // when
        ManualSupplierService.Result result = service.create("store-1", "bad/name:x");

        // then
        assertFalse(result.ok());
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
    void listExposesEnabledFlagFromConnection() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true);
        connection.setEnabled(false);
        Store store = storeWith(connection);

        // when
        ManualSupplierService.ManualSupplierView view = service.list(store).get(0);

        // then
        assertFalse(view.enabled());
        assertEquals("Hurtownia A", view.label());
    }

    @Test
    void applySelectionsUpdatesEnabledAndFlags() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        lenient().when(storeFeedRepository.canRead("store-1", "manual:Hurtownia A", "csv")).thenReturn(true);
        ManualSupplierService.ManualSelection selection =
                new ManualSupplierService.ManualSelection("manual:Hurtownia A", false, false, true);

        // when
        service.applySelections("store-1", List.of(selection));

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertFalse(connection.isEnabled());
        assertFalse(connection.isIncludeInPricing());
        assertTrue(connection.isIncludeInFulfilment());
        verify(storesRepository).save(store);
    }

    @Test
    void applySelectionsForcesEnabledFalseWhenNoFeed() {
        // given
        Store store = storeWith(new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, true));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeFeedRepository.canRead("store-1", "manual:Hurtownia A", "csv")).thenReturn(false);
        ManualSupplierService.ManualSelection selection =
                new ManualSupplierService.ManualSelection("manual:Hurtownia A", true, true, true);

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
                new ManualSupplierService.ManualSelection("manual:Hurtownia A", true, true, true);

        // when
        service.applySelections("store-1", List.of(selection));

        // then
        StoreSupplierConnection connection = store.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertTrue(connection.isEnabled());
        verify(storesRepository).save(store);
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
        when(supplierRegistry.getAllSupplierNames()).thenReturn(List.of("Acme"));

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
                List.of(new ManualSupplierService.ManualSelection("manual:Hurtownia A", true, true, true)));

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
