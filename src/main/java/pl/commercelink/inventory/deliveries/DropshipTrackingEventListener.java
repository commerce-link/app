package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DropshipTrackingEventListener {

    private final DropshipTrackingService dropshipTrackingService;

    @SqsListener(
            value = "supplier-order-tracking-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(DropshipTrackingEventRequest payload) {
        dropshipTrackingService.check(payload.getStoreId(), payload.getDeliveryId());
    }
}
