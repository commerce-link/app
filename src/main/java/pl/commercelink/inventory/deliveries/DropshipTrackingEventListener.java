package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropshipTrackingEventListener {

    private final DropshipTrackingService dropshipTrackingService;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "supplier-order-tracking-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(DropshipTrackingEventRequest payload) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.warn("Dropship tracking of delivery {} skipped: store {} is inactive",
                    payload.getDeliveryId(), payload.getStoreId());
            return;
        }
        dropshipTrackingService.check(payload.getStoreId(), payload.getDeliveryId());
    }
}
