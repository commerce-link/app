package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.StoreActivity;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptWorkListenerTest {

    @Mock private ReceiptProcessor processor;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private ReceiptWorkListener listener;

    @Test
    void processesAttemptOfActiveStore() {
        // given
        when(storeActivity.isActive("s1")).thenReturn(true);

        // when
        listener.handleMessage(new ReceiptWorkRequest("s1", "order-1:R1"));

        // then
        verify(processor).process("s1", "order-1:R1");
    }

    @Test
    void leavesAttemptOfInactiveStoreUntouched() {
        // given
        when(storeActivity.isActive("s1")).thenReturn(false);

        // when
        listener.handleMessage(new ReceiptWorkRequest("s1", "order-1:R1"));

        // then
        verifyNoInteractions(processor);
    }
}
