package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseCompletionEventListenerTest {

    @Mock
    private SupplierPurchaseService supplierPurchaseService;
    @InjectMocks
    private SupplierPurchaseCompletionEventListener listener;

    @Test
    void passesParsedReceiveCountToService() {
        // given
        SupplierPurchaseCompletionEventRequest payload =
                new SupplierPurchaseCompletionEventRequest("s1", "d1", "ref-1", null);

        // when
        listener.handleMessage(payload, "3");

        // then
        verify(supplierPurchaseService).completeAwaitingPurchase(payload, 3);
    }
}
