package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.StoreInventoryProvider;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierFeedServiceTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierConfigurationManager configurationManager;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StoreFeedRepository storeFeedRepository;
    @Mock
    private StoreInventoryProvider storeInventoryProvider;

    @InjectMocks
    private StoreSupplierFeedService service;

    private Store storeWithId(String id) {
        Store store = new Store();
        store.setStoreId(id);
        return store;
    }

    @Test
    void downloadsFeedToPerStoreKeyThenInvalidatesCache() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        Map<String, String> config = Map.of("url", "https://feed/x.csv");
        byte[] data = "rows".getBytes();

        when(storesRepository.findById("store-1")).thenReturn(store);
        when(configurationManager.loadConfiguration(store, "Wortmann")).thenReturn(config);
        when(supplierRegistry.downloadFeed("Wortmann", config)).thenReturn(Optional.of(new FeedData(data, "csv")));

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(supplierRegistry).downloadFeed("Wortmann", config);
        InOrder inOrder = inOrder(storeFeedRepository, storeInventoryProvider);
        inOrder.verify(storeFeedRepository).store("store-1", "Wortmann", data, "csv");
        inOrder.verify(storeInventoryProvider).invalidate("store-1");
    }

    @Test
    void doesNothingWhenStoreNotFound() throws ResourceDownloadException {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when
        service.loadStoreFeed("missing", "Wortmann");

        // then
        verifyNoInteractions(supplierRegistry);
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
        verify(storeInventoryProvider, never()).invalidate(anyString());
    }

    @Test
    void doesNotStoreWhenDownloadReturnsEmpty() throws ResourceDownloadException {
        // given
        Store store = storeWithId("store-1");
        Map<String, String> config = Map.of("url", "https://feed/x.csv");

        when(storesRepository.findById("store-1")).thenReturn(store);
        when(configurationManager.loadConfiguration(store, "Wortmann")).thenReturn(config);
        when(supplierRegistry.downloadFeed("Wortmann", config)).thenReturn(Optional.empty());

        // when
        service.loadStoreFeed("store-1", "Wortmann");

        // then
        verify(storeFeedRepository, never()).store(anyString(), anyString(), any(byte[].class), anyString());
        verify(storeInventoryProvider, never()).invalidate(anyString());
    }
}
