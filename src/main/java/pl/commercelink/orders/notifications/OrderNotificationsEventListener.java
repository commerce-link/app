package pl.commercelink.orders.notifications;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class OrderNotificationsEventListener {

    private final OrdersRepository ordersRepository;
    private final OrderNotificationsService orderNotificationsService;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "order-notifications-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderNotificationsEventRequest payload) {
        if (!storeActivity.isActive(payload.storeId())) {
            log.warn("Notifications of order {} skipped: store {} is inactive", payload.orderId(), payload.storeId());
            return;
        }
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
