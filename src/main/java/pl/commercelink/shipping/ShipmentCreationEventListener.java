package pl.commercelink.shipping;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentCreationEventListener {

    private final ShipmentCreationChecker checker;

    @SqsListener(
            value = ShipmentCreationEventPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(ShipmentCreationCheckRequest payload) {
        checker.check(payload);
    }
}
