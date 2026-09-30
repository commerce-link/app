package pl.commercelink.shipping;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentTrackingEventListener {

    private final ShipmentTrackingSubscriber subscriber;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "shipment-tracking-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(ShipmentTrackingCheckRequest payload,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT) String receiveCount) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.warn("Shipment tracking of order {} skipped: store {} is inactive",
                    payload.getOrderId(), payload.getStoreId());
            return;
        }
        subscriber.check(payload, Integer.parseInt(receiveCount));
    }
}
