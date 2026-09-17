package pl.commercelink.orders;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderLifecycleEventPublisher {

    @Value("${application.env}")
    private String env;

    @Value("${marketplace.listeners.enabled:false}")
    private boolean marketplaceListenersEnabled;

    private final SqsTemplate sqsTemplate;

    public void publish(Order order, OrderLifecycleEventType eventType) {
        if (!marketplaceListenersEnabled && !env.equals("prod")) {
            return;
        }

        if (!order.isMarketplaceOrder()) {
            return;
        }

        OrderLifecycleEvent event = new OrderLifecycleEvent(
                order.getStoreId(),
                order.getOrderId(),
                eventType,
                order.getExternalOrderId(),
                order.getSource().getName()
        );

        sqsTemplate.send("marketplace-order-lifecycle-queue", event);
    }
}
