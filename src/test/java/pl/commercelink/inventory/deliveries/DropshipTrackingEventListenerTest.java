package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropshipTrackingEventListenerTest {

    @Mock
    private DropshipTrackingService dropshipTrackingService;
    @InjectMocks
    private DropshipTrackingEventListener listener;

    @Test
    void delegatesToTheTrackingService() {
        // given
        DropshipTrackingEventRequest payload = new DropshipTrackingEventRequest("s1", "d1", "ACME-DS-1");

        // when
        listener.handleMessage(payload);

        // then
        verify(dropshipTrackingService).check("s1", "d1");
    }
}
