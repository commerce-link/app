package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderShipmentOwner extends StoredShipmentOwner<Order> {

    private final OrdersRepository ordersRepository;
    private final ShipmentTrackingSubscriber trackingSubscriber;
    private final OrderLifecycle orderLifecycle;
    private final OrderLifecycleEventPublisher lifecycleEventPublisher;

    public OrderShipmentOwner(OrdersRepository ordersRepository, OptimisticLockingExecutor optimisticLockingExecutor,
                              ShipmentTrackingSubscriber trackingSubscriber, OrderLifecycle orderLifecycle,
                              OrderLifecycleEventPublisher lifecycleEventPublisher) {
        super(optimisticLockingExecutor);
        this.ordersRepository = ordersRepository;
        this.trackingSubscriber = trackingSubscriber;
        this.orderLifecycle = orderLifecycle;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.ORDER;
    }

    /**
     * The new shipment takes the place of the ones {@link #keepsItsPlace} lets go and inherits the delivery choice, as
     * a courier booking always did.
     */
    @Override
    public boolean markCreating(ShipmentCreationCheckRequest request, Shipment placeholder) {
        return modify(request, order -> {
            // the page's check is not atomic: a second command next to one in flight would pay for a second label
            if (order.hasShipmentBeingCreated()) {
                return false;
            }
            List<Shipment> kept = order.getShipments().stream().filter(OrderShipmentOwner::keepsItsPlace).toList();
            // the delivery choice goes onto the new shipment only, never onto the shipments that stay
            order.replaceShipments(new ArrayList<>(List.of(placeholder)));
            List<Shipment> next = new ArrayList<>(kept);
            next.add(placeholder);
            order.setShipments(next);
            return true;
        });
    }

    /** What a courier booking always did, now on the order as saved. */
    @Override
    protected void afterCreated(ShipmentCreationCheckRequest request) {
        Order order = ordersRepository.findById(request.getStoreId(), request.getOwnerId());
        trackingSubscriber.subscribe(request.getStoreId(), order);
        orderLifecycle.update(order);
        lifecycleEventPublisher.publish(order, OrderLifecycleEventType.ShipmentCreated);
    }

    @Override
    protected Order load(String storeId, String ownerId) {
        return ordersRepository.findById(storeId, ownerId);
    }

    @Override
    protected void save(Order order) {
        ordersRepository.save(order);
    }

    @Override
    protected List<Shipment> shipments(Order order) {
        return order.getShipments();
    }
}
