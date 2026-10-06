package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentCreationEventListenerTest {

    @Mock
    private ShipmentCreationChecker checker;

    @InjectMocks
    private ShipmentCreationEventListener listener;

    @Test
    void passesTheRequestToTheChecker() {
        // given
        ShipmentCreationCheckRequest request = ShipmentCreationCheckRequest.builder().storeId("store-1")
                .ownerType(ShipmentOwnerType.ORDER).ownerId("order-1").commandId("cmd-1").attempt(1).build();

        // when
        listener.handleMessage(request);

        // then
        verify(checker).check(request);
    }
}
