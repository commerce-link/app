package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseEventListenerTest {

    @Mock
    private SupplierPurchaseService supplierPurchaseService;

    @InjectMocks
    private SupplierPurchaseEventListener listener;

    @Test
    void handleMessageDelegatesToProcessPending() {
        // given
        SupplierPurchaseEventRequest payload = new SupplierPurchaseEventRequest(
                "store-1", "delivery-1", "Acme", "ref-1");

        // when
        listener.handleMessage(payload);

        // then
        verify(supplierPurchaseService).processPending("store-1", "delivery-1");
    }
}
