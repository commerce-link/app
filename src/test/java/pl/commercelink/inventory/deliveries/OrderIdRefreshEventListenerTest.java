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
class OrderIdRefreshEventListenerTest {

    @Mock
    private OrderIdRefreshService orderIdRefreshService;
    @Mock
    private StoreActivity storeActivity;

    @InjectMocks
    private OrderIdRefreshEventListener listener;

    @BeforeEach
    void storesAreActive() {
        lenient().when(storeActivity.isActive(anyString())).thenReturn(true);
    }

    @Test
    void passesParsedReceiveCountToService() {
        // given
        OrderIdRefreshEventRequest payload = new OrderIdRefreshEventRequest("s1", "d1", "IncomGroup", "ref-1");

        // when
        listener.handleMessage(payload, "3");

        // then
        verify(orderIdRefreshService).refresh(payload, 3);
    }

    @Test
    void skipsRefreshOfInactiveStore() {
        // given
        OrderIdRefreshEventRequest payload = new OrderIdRefreshEventRequest("s1", "d1", "IncomGroup", "ref-1");
        when(storeActivity.isActive("s1")).thenReturn(false);

        // when
        listener.handleMessage(payload, "1");

        // then
        verifyNoInteractions(orderIdRefreshService);
    }
}
