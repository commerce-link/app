package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.StoreActivity;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseEventListenerTest {

    @Mock
    private SupplierPurchaseService supplierPurchaseService;
    @Mock
    private StoreActivity storeActivity;

    @InjectMocks
    private SupplierPurchaseEventListener listener;

    @BeforeEach
    void storesAreActive() {
        lenient().when(storeActivity.isActive(anyString())).thenReturn(true);
    }

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

    @Test
    void dropsPurchaseOfInactiveStore() {
        // given
        SupplierPurchaseEventRequest payload = new SupplierPurchaseEventRequest(
                "store-1", "delivery-1", "Acme", "ref-1", "order-1");
        when(storeActivity.isActive("store-1")).thenReturn(false);

        // when
        listener.handleMessage(payload, "1");

        // then
        verifyNoInteractions(supplierPurchaseService);
    }
}
