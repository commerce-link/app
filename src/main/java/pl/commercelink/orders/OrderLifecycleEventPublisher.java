package pl.commercelink.orders;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderLifecycleEventPublisher {

    @Value("${application.env}")
    private String env;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(Order order, OrderLifecycleEventType eventType) {
        if (!env.equals("prod")) {
            return;
        }

        if (!order.isMarketplaceOrder()) {
            return;
        }

        OrderLifecycleEvent event = new OrderLifecycleEvent(
                order.getStoreId(),
                order.getOrderId(),
                eventType
        );

        sqsTemplate.send("marketplace-order-lifecycle-queue", event);
    }
}
