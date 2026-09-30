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

import java.util.Collections;

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
     * Cancels the courier order at the carrier and leaves a bare shipment of the same type. When that leaves a Shipping
     * order with nothing shipped it goes back to Realization, as a removal of the shipment would
     * (OrderRealizationStepBack, no e-mail to the customer). The order is saved directly, as before: the step back is the
     * only status change, and the lifecycle would change nothing on a Realization order whose shipment has not gone out.
     * Returns whether the order went back.
     */
    public boolean cancelShipping(String orderId, String storeId) {
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);

        Shipment shipment = order.firstShipmentWithShippingData()
                .orElseThrow(() -> new ShippingException("No valid shipment data to cancel"));

        if (shipment.getExternalId() == null) {
            throw new ShippingException("Shipment has no external package ID");
        }

        ShippingProvider shippingProvider = shippingProviderFactory.get(store);
        shippingProvider.cancelShipment(shipment.getExternalId());

        order.replaceShipments(Collections.singletonList(new Shipment(shipment.getType())));
        boolean backToRealization = realizationStepBack.apply(order);
        orderEventsRepository.deleteByOrderIdAndName(orderId, EmailNotificationType.ORDER_SHIPPING.name());
        ordersRepository.save(order);
        return backToRealization;
    }
}
