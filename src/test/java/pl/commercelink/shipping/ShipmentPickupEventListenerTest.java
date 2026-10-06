package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentPickupEventListenerTest {

    @Mock
    private ShipmentPickupChecker checker;

    @InjectMocks
    private ShipmentPickupEventListener listener;

    @Test
    void passesTheRequestToTheChecker() {
        // given
        ShipmentPickupCheckRequest request = ShipmentPickupCheckRequest.builder().storeId("store-1")
                .commandId("cmd-1").attempt(1).build();

        // when
        listener.handleMessage(request);

        // then
        verify(checker).check(request);
    }
}
