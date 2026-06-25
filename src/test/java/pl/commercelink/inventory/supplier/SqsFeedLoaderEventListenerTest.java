package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SqsFeedLoaderEventListener.FeedLoaderEventPayload;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SqsFeedLoaderEventListenerTest {

    @Mock
    private StoreSupplierFeedService storeSupplierFeedService;
    @Mock
    private GlobalSupplierFeedService globalSupplierFeedService;

    @InjectMocks
    private SqsFeedLoaderEventListener listener;

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
    void globalPayloadUsesGlobalFeedService() throws Exception {
        // when
        listener.handleMessage(payload("Wortmann", null));

        // then
        verify(globalSupplierFeedService).loadFeed("Wortmann");
        verifyNoInteractions(storeSupplierFeedService);
    }

    @Test
    void storeScopedPayloadDelegatesToStoreSupplierFeedService() throws Exception {
        // when
        listener.handleMessage(payload("Wortmann", "store-1"));

        // then
        verify(storeSupplierFeedService).loadStoreFeed("store-1", "Wortmann");
        verify(globalSupplierFeedService, never()).loadFeed(anyString());
    }

    @Test
    void propagatesExceptionOnStoreFeedFailure() throws Exception {
        // given
        doThrow(new ResourceDownloadException("timeout", new RuntimeException()))
                .when(storeSupplierFeedService).loadStoreFeed("store-1", "Wortmann");

        // when / then
        assertThrows(Exception.class, () -> listener.handleMessage(payload("Wortmann", "store-1")));
    }

    @Test
    void propagatesExceptionOnGlobalFeedFailure() throws Exception {
        // given
        doThrow(new ResourceDownloadException("connection refused", new RuntimeException()))
                .when(globalSupplierFeedService).loadFeed("Wortmann");

        // when / then
        assertThrows(Exception.class, () -> listener.handleMessage(payload("Wortmann", null)));
    }
}
