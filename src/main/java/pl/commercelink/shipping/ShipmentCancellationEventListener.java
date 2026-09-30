package pl.commercelink.shipping;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentCancellationEventListener {

    private final ShipmentCancellationChecker checker;

    @SqsListener(
            value = ShipmentCancellationEventPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(ShipmentCancellationCheckRequest payload) {
        checker.check(payload);
    }
}
