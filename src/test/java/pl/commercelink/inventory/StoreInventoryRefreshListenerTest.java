package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreInventoryRefreshListenerTest {

    @Mock
    private StoreInventoryProvider storeInventoryProvider;

    @InjectMocks
    private StoreInventoryRefreshListener listener;

    @Test
    void rebuildsTheStoreNamedInTheRequest() {
        // when
        listener.handleMessage(new StoreInventoryRefreshRequest("store-1", 4_242L));

        // then
        verify(storeInventoryProvider).refresh("store-1", 4_242L);
    }

    @Test
    void requestSentByHandCarriesNoTime() {
        // when
        listener.handleMessage(new StoreInventoryRefreshRequest("store-1", null));

        // then
        verify(storeInventoryProvider).refresh("store-1", null);
    }

    @Test
    void failedRebuildIsLeftToTheQueueToDeliverAgain() {
        // given
        doThrow(new IllegalStateException("rates unavailable")).when(storeInventoryProvider).refresh("store-1", 4_242L);

        // when / then
        assertThrows(IllegalStateException.class,
                () -> listener.handleMessage(new StoreInventoryRefreshRequest("store-1", 4_242L)));
    }
}
