package pl.commercelink.orders.fulfilment;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;

import java.util.List;

@Component
public class OrderFulfilmentEventPublisher {

    private static final String QUEUE_NAME = "order-fulfilment-queue.fifo";

    @Value("${application.env}")
    private String env;

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private AutomatedOrderFulfilment automatedOrderFulfilment;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    public void publish(String storeId, String orderId) {
        if (!env.equals("prod")) {
            List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);
            automatedOrderFulfilment.run(storeId, orderItems);
            return;
        }

        OrderFulfilmentRequest request = new OrderFulfilmentRequest(storeId, orderId);

        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .messageGroupId(storeId)
                .messageDeduplicationId(orderId)
        );
    }
}
