package pl.commercelink.warehouse;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;

@Component
public class GoodsOutEventPublisher {

    private static final String QUEUE_NAME = "order-goods-out-queue.fifo";

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(Order order, String createdBy) {
        GoodsOutEventRequest request = new GoodsOutEventRequest(
                order.getStoreId(),
                order.getOrderId(),
                createdBy
        );

        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .messageGroupId(order.getStoreId())
                .messageDeduplicationId(order.getOrderId())
        );
    }
}
