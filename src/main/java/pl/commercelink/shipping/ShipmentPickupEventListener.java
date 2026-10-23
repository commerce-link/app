package pl.commercelink.shipping;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentPickupEventListener {

    private final ShipmentPickupChecker checker;

    @SqsListener(
            value = ShipmentPickupEventPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(ShipmentPickupCheckRequest payload) {
        checker.check(payload);
    }
}
