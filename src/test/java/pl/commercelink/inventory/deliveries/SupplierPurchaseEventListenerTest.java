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
        listener.handleMessage(payload, "3");

        // then
        verify(supplierPurchaseService).processPending("store-1", "delivery-1", null, 3);
    }

    @Test
    void handleMessagePassesThroughThePayloadOrderId() {
        // given
        SupplierPurchaseEventRequest payload = new SupplierPurchaseEventRequest(
                "store-1", "delivery-1", "Acme", "ref-1", "order-1");

        // when
        listener.handleMessage(payload, "1");

        // then
        verify(supplierPurchaseService).processPending("store-1", "delivery-1", "order-1", 1);
    }
}
