package pl.commercelink.inventory.deliveries;

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
public class OrderIdRefreshEventListener {

    private final OrderIdRefreshService orderIdRefreshService;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "supplier-order-refresh-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderIdRefreshEventRequest payload,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT) String receiveCount) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.warn("Supplier order id refresh of delivery {} skipped: store {} is inactive",
                    payload.getDeliveryId(), payload.getStoreId());
            return;
        }
        orderIdRefreshService.refresh(payload, Integer.parseInt(receiveCount));
    }
}
