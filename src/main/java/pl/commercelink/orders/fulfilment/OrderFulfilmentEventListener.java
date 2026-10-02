package pl.commercelink.orders.fulfilment;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;

import java.util.List;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class OrderFulfilmentEventListener {

    @Autowired
    private AutomatedOrderFulfilment automatedOrderFulfilment;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @SqsListener(
            value = "order-fulfilment-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderFulfilmentRequest payload) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(payload.getOrderId());
        if (orderItems.isEmpty()) {
            return;
        }

        automatedOrderFulfilment.run(payload.getStoreId(), orderItems);
    }
}
