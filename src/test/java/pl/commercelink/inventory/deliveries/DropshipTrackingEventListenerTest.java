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
class DropshipTrackingEventListenerTest {

    @Mock
    private DropshipTrackingService dropshipTrackingService;
    @Mock
    private StoreActivity storeActivity;

    @InjectMocks
    private DropshipTrackingEventListener listener;

    @BeforeEach
    void storesAreActive() {
        lenient().when(storeActivity.isActive(anyString())).thenReturn(true);
    }

    @Test
    void delegatesToTheTrackingService() {
        // given
        DropshipTrackingEventRequest payload = new DropshipTrackingEventRequest("s1", "d1", "ACME-DS-1");

        // when
        listener.handleMessage(payload);

        // then
        verify(dropshipTrackingService).check("s1", "d1");
    }

    @Test
    void skipsTrackingOfInactiveStore() {
        // given
        DropshipTrackingEventRequest payload = new DropshipTrackingEventRequest("s1", "d1", "ACME-DS-1");
        when(storeActivity.isActive("s1")).thenReturn(false);

        // when
        listener.handleMessage(payload);

        // then
        verifyNoInteractions(dropshipTrackingService);
    }
}
