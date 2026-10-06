package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderShipmentOwner extends StoredShipmentOwner<Order> {

    private final OrdersRepository ordersRepository;

    public OrderShipmentOwner(OrdersRepository ordersRepository, OptimisticLockingExecutor optimisticLockingExecutor) {
        super(optimisticLockingExecutor);
        this.ordersRepository = ordersRepository;
    }

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.ORDER;
    }

    /**
     * The new shipment takes the place of the ones without any data (the customer's bare delivery choice, a failed
     * creation) and inherits the delivery choice, as a courier booking always did. Shipments with a courier order or
     * with data stay as they were: dropping them would lose a paid label nobody could cancel any more.
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

    private static boolean keepsItsPlace(Shipment shipment) {
        return shipment.getExternalId() != null || shipment.hasShippingData() || shipment.hasCollectionData();
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
