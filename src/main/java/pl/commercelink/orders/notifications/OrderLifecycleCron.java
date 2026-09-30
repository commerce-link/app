package pl.commercelink.orders.notifications;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class OrderLifecycleCron {

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderLifecycle orderLifecycle;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "order-lifecycle-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void processDeliveredOrders(String message) {
        List<Store> stores = storesRepository.findAll();

        for (Store store : stores) {
            if (!storeActivity.isActive(store)) {
                continue;
            }
            ordersRepository.findAllByStoreIdAndStatus(store.getStoreId(), OrderStatus.Shipping, OrderStatus.Delivered)
                    .forEach(order -> orderLifecycle.update(order));
        }
    }

}