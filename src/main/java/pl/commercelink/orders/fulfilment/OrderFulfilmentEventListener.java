package pl.commercelink.orders.fulfilment;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.stores.StoreActivity;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class OrderFulfilmentEventListener {

    private final AutomatedOrderFulfilment automatedOrderFulfilment;
    private final OrderItemsRepository orderItemsRepository;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "order-fulfilment-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderFulfilmentRequest payload) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.warn("Automated fulfilment of order {} skipped: store {} is inactive",
                    payload.getOrderId(), payload.getStoreId());
            return;
        }
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(payload.getOrderId());
        if (orderItems.isEmpty()) {
            return;
        }

        automatedOrderFulfilment.run(payload.getStoreId(), orderItems);
    }
}
