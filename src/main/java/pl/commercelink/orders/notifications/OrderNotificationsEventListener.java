package pl.commercelink.orders.notifications;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class OrderNotificationsEventListener {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderNotificationsService orderNotificationsService;

    @SqsListener(
            value = "order-notifications-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderNotificationsEventRequest payload) {
        Order order = ordersRepository.findById(payload.storeId(), payload.orderId());
        if (order == null) {
            return;
        }

        if (payload.oldAssemblyDate() != null) {
            orderNotificationsService.sendOrderAssemblyDateChangedEmailNotification(order, payload.oldAssemblyDate());
        } else {
            orderNotificationsService.send(order);
        }
    }
}
