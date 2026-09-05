package pl.commercelink.orders;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.rma.RMA;

@Component
@RequiredArgsConstructor
public class OrderLifecycleEventPublisher {

    @Value("${application.env}")
    private String env;

    private final SqsTemplate sqsTemplate;

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
                eventType,
                order.getExternalOrderId(),
                order.getSource().getName()
        );

        sqsTemplate.send("marketplace-order-lifecycle-queue", event);
    }

    public void publishReturnAction(Order order, RMA rma, OrderLifecycleEventType eventType,
                                    MarketplaceReturnAction action) {
        if (!env.equals("prod")) {
            return;
        }
        if (!order.isMarketplaceOrder() || rma == null || !rma.isMarketplaceReturn()) {
            return;
        }
        OrderLifecycleEvent event = new OrderLifecycleEvent(
                order.getStoreId(),
                order.getOrderId(),
                eventType,
                order.getExternalOrderId(),
                order.getSource().getName(),
                action
        );
        sqsTemplate.send("marketplace-order-lifecycle-queue", event);
    }
}
