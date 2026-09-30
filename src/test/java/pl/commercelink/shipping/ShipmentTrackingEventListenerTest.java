package pl.commercelink.shipping;

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
class ShipmentTrackingEventListenerTest {

    @Mock
    private ShipmentTrackingSubscriber subscriber;
    @Mock
    private StoreActivity storeActivity;

    @InjectMocks
    private ShipmentTrackingEventListener listener;

    @BeforeEach
    void storesAreActive() {
        lenient().when(storeActivity.isActive(anyString())).thenReturn(true);
    }

    @Test
    void passesReceiveCountAsAttempt() {
        // given
        ShipmentTrackingCheckRequest request = new ShipmentTrackingCheckRequest("store-1", "order-1", "PKG-1");

        // when
        listener.handleMessage(request, "3");

        // then
        verify(subscriber).check(request, 3);
    }

    @Test
    void skipsTrackingOfInactiveStore() {
        // given
        ShipmentTrackingCheckRequest request = new ShipmentTrackingCheckRequest("store-1", "order-1", "PKG-1");
        when(storeActivity.isActive("store-1")).thenReturn(false);

        // when
        listener.handleMessage(request, "1");

        // then
        verifyNoInteractions(subscriber);
    }
}
