package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderIdRefreshEventListener {

    private final OrderIdRefreshService orderIdRefreshService;

    @SqsListener(
            value = "supplier-order-refresh-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderIdRefreshEventRequest payload,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT) String receiveCount) {
        orderIdRefreshService.refresh(payload, Integer.parseInt(receiveCount));
    }
}
