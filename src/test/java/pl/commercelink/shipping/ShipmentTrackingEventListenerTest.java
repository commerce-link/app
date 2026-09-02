package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentTrackingEventListenerTest {

    @Mock
    private ShipmentTrackingSubscriber subscriber;

    @InjectMocks
    private ShipmentTrackingEventListener listener;

    @Test
    void passesReceiveCountAsAttempt() {
        // given
        ShipmentTrackingCheckRequest request = new ShipmentTrackingCheckRequest("store-1", "order-1", "PKG-1");

        // when
        listener.handleMessage(request, "3");

        // then
        verify(subscriber).check(request, 3);
    }
}
