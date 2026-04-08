package pl.commercelink.orders.notifications;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;

import java.time.LocalDate;

@Component
public class OrderNotificationsEventPublisher {

    private static final String QUEUE_NAME = "order-notifications-queue";

    @Value("${application.env}")
    private String env;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(Order order) {
        if (!"prod".equals(env)) {
            return;
        }

        OrderNotificationsEventRequest request = new OrderNotificationsEventRequest(
                order.getStoreId(),
                order.getOrderId()
        );

        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
        );
    }

    public void publishAssemblyDateChanged(Order order, LocalDate oldAssemblyDate) {
        if (!"prod".equals(env)) {
            return;
        }

        OrderNotificationsEventRequest request = new OrderNotificationsEventRequest(
                order.getStoreId(),
                order.getOrderId(),
                oldAssemblyDate
        );

        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
        );
    }
}
