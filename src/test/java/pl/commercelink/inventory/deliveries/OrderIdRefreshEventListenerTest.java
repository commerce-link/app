package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderIdRefreshEventListenerTest {

    @Mock
    private OrderIdRefreshService orderIdRefreshService;
    @InjectMocks
    private OrderIdRefreshEventListener listener;

    @Test
    void passesParsedReceiveCountToService() {
        // given
        OrderIdRefreshEventRequest payload = new OrderIdRefreshEventRequest("s1", "d1", "IncomGroup", "ref-1");

        // when
        listener.handleMessage(payload, "3");

        // then
        verify(orderIdRefreshService).refresh(payload, 3);
    }
}
