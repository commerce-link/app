package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DropshipOrderCompletion {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final OrderLifecycle orderLifecycle;

    public void markSuppliedByDropship(String storeId, String orderId, String deliveryId) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return;
        }
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);
        for (OrderItem orderItem : orderItems) {
            if (deliveryId.equals(orderItem.getDeliveryId()) && orderItem.isWaitingForCollection()) {
                orderItem.markAsReceived();
                orderItemsRepository.save(orderItem);
            }
        }
        order.updateEstimatedAssemblyAt(LocalDate.now());
        orderLifecycle.update(order, orderItems);
    }
}
