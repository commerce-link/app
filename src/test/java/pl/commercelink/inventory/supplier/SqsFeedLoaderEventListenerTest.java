package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.SqsFeedLoaderEventListener.FeedLoaderEventPayload;
import pl.commercelink.inventory.supplier.api.FeedData;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.Mockito.*;

class SqsFeedLoaderEventListenerTest {

    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final StoreSupplierFeedService storeSupplierFeedService = mock(StoreSupplierFeedService.class);

    private final SqsFeedLoaderEventListener listener =
            new SqsFeedLoaderEventListener(supplierRegistry, inventoryRepository, storeSupplierFeedService);

    private FeedLoaderEventPayload payload(String supplierName, String storeId) throws Exception {
        FeedLoaderEventPayload payload = new FeedLoaderEventPayload();
        setField(payload, "supplierName", supplierName);
        if (storeId != null) {
            setField(payload, "storeId", storeId);
        }
        return payload;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void globalPayloadUsesGlobalDownloadPath() throws Exception {
        when(supplierRegistry.downloadFeed("Wortmann"))
                .thenReturn(Optional.of(FeedData.csv("rows".getBytes())));

        listener.handleMessage(payload("Wortmann", null));

        verify(supplierRegistry).downloadFeed("Wortmann");
        verify(inventoryRepository).store(eq("Wortmann"), any(byte[].class), eq("csv"));
        verifyNoInteractions(storeSupplierFeedService);
    }

    @Test
    void storeScopedPayloadDelegatesToStoreSupplierFeedService() throws Exception {
        listener.handleMessage(payload("Wortmann", "store-1"));

        verify(storeSupplierFeedService).loadStoreFeed("store-1", "Wortmann");
        verify(supplierRegistry, never()).downloadFeed(anyString());
    }
}
