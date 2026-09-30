package pl.commercelink.shipping;

import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShipmentCancelService {

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final OrderRealizationStepBack realizationStepBack;

    public ShipmentCancelService(StoresRepository storesRepository, OrdersRepository ordersRepository,
                                 OrderEventsRepository orderEventsRepository, ShippingProviderFactory shippingProviderFactory,
                                 OrderRealizationStepBack realizationStepBack) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.shippingProviderFactory = shippingProviderFactory;
        this.realizationStepBack = realizationStepBack;
    }

    /**
     * Cancels the courier order at the carrier and removes the shipments of that courier order (every parcel carries its
     * externalId); the other shipments stay as they are. When no shipment is left, a bare one of the same type keeps the
     * customer's delivery choice. When what is left of a Shipping order has nothing shipped it goes back to Realization,
     * as a removal of the shipment would (OrderRealizationStepBack, no e-mail to the customer), and the shipping e-mail
     * is forgotten once no other shipment carries it. The order is saved directly, as before: the step back is the only
     * status change, and the lifecycle would change nothing on a Realization order whose shipment has not gone out.
     * Returns whether the order went back. A store whose shipping provider is gone (its authorisation lost) gets
     * ShippingUnavailableException before anything changes.
     */
    public boolean cancelShipping(String orderId, String storeId) {
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);

        // by the courier order, not by the shipped date: the paid label is there whatever the dates say
        Shipment shipment = order.courierShipmentToCancel()
                .orElseThrow(() -> new ShippingException("No courier order to cancel"));

        ShippingProvider shippingProvider = shippingProviderFactory.get(store);
        if (shippingProvider == null) {
            throw new ShippingUnavailableException(storeId);
        }
        shippingProvider.cancelShipment(shipment.getExternalId());

        String courierOrderId = shipment.getExternalId();
        List<Shipment> remaining = order.getShipments().stream()
                .filter(s -> !courierOrderId.equals(s.getExternalId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (remaining.isEmpty()) {
            // the list still holds the cancelled shipments, whose delivery choice the bare one inherits
            order.replaceShipments(new ArrayList<>(List.of(new Shipment(shipment.getType()))));
        } else {
            order.setShipments(remaining);
        }
        boolean backToRealization = realizationStepBack.apply(order);
        if (order.firstShipmentWithShippingData().isEmpty()) {
            orderEventsRepository.deleteByOrderIdAndName(orderId, EmailNotificationType.ORDER_SHIPPING.name());
        }
        ordersRepository.save(order);
        return backToRealization;
    }
}
