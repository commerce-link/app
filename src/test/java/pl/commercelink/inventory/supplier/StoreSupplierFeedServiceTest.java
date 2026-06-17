package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.StoreInventoryProvider;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class StoreSupplierFeedServiceTest {

    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final SupplierConfigurationManager configurationManager = mock(SupplierConfigurationManager.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final StoreInventoryProvider storeInventoryProvider = mock(StoreInventoryProvider.class);

    private final StoreSupplierFeedService service = new StoreSupplierFeedService(
            storesRepository, configurationManager, supplierRegistry, inventoryRepository, storeInventoryProvider);

    private Store storeWithId(String id) {
        Store store = new Store();
        store.setStoreId(id);
        return store;
    }

    @Test
    void downloadsFeedWithStoreConfigAndStoresToPerStoreKey() throws ResourceDownloadException {
        Store store = storeWithId("store-1");
        Map<String, String> config = Map.of("url", "https://feed/x.csv");
        byte[] data = "rows".getBytes();

        when(storesRepository.findById("store-1")).thenReturn(store);
        when(configurationManager.loadConfiguration(store, "Wortmann")).thenReturn(config);
        when(supplierRegistry.downloadFeed("Wortmann", config)).thenReturn(Optional.of(new FeedData(data, "csv")));

        service.loadStoreFeed("store-1", "Wortmann");

        verify(supplierRegistry).downloadFeed("Wortmann", config);
        verify(inventoryRepository).store("store-1", "Wortmann", data, "csv");
    }

    @Test
    void invalidatesStoreCacheAfterStoringFeed() throws ResourceDownloadException {
        Store store = storeWithId("store-1");
        Map<String, String> config = Map.of("url", "https://feed/x.csv");
        byte[] data = "rows".getBytes();

        when(storesRepository.findById("store-1")).thenReturn(store);
        when(configurationManager.loadConfiguration(store, "Wortmann")).thenReturn(config);
        when(supplierRegistry.downloadFeed("Wortmann", config)).thenReturn(Optional.of(new FeedData(data, "csv")));

        service.loadStoreFeed("store-1", "Wortmann");

        verify(storeInventoryProvider).invalidate("store-1");
    }

    @Test
    void doesNothingWhenStoreNotFound() throws ResourceDownloadException {
        when(storesRepository.findById("missing")).thenReturn(null);

        service.loadStoreFeed("missing", "Wortmann");

        verifyNoInteractions(supplierRegistry);
        verify(inventoryRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
        verify(storeInventoryProvider, never()).invalidate(anyString());
    }

    @Test
    void doesNotStoreWhenDownloadReturnsEmpty() throws ResourceDownloadException {
        Store store = storeWithId("store-1");
        Map<String, String> config = Map.of("url", "https://feed/x.csv");

        when(storesRepository.findById("store-1")).thenReturn(store);
        when(configurationManager.loadConfiguration(store, "Wortmann")).thenReturn(config);
        when(supplierRegistry.downloadFeed("Wortmann", config)).thenReturn(Optional.empty());

        service.loadStoreFeed("store-1", "Wortmann");

        verify(inventoryRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
        verify(storeInventoryProvider, never()).invalidate(anyString());
    }
}
