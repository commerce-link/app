package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentCancellationEventListenerTest {

    @Mock
    private ShipmentCancellationChecker checker;

    @InjectMocks
    private ShipmentCancellationEventListener listener;

    @Test
    void passesTheRequestToTheChecker() {
        // given
        ShipmentCancellationCheckRequest request = ShipmentCancellationCheckRequest.first("store-1", "order-1", "PKG-1", "cmd-1");

        // when
        listener.handleMessage(request);

        // then
        verify(checker).check(request);
    }
}
