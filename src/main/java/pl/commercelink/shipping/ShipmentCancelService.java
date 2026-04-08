package pl.commercelink.shipping;

import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
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

    public ShipmentCancelService(StoresRepository storesRepository, OrdersRepository ordersRepository,
                                 OrderEventsRepository orderEventsRepository, ShippingProviderFactory shippingProviderFactory) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.shippingProviderFactory = shippingProviderFactory;
    }

    public void cancelShipping(String orderId, String storeId) {
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);

        Shipment shipment = order.getShipments().stream()
                .filter(Shipment::hasShippingData)
                .findFirst()
                .orElseThrow(() -> new ShippingException("No valid shipment data to cancel"));

        if (shipment.getExternalId() == null) {
            throw new ShippingException("Shipment has no external package ID");
        }

        ShippingProvider shippingProvider = shippingProviderFactory.get(store);
        shippingProvider.cancelShipment(shipment.getExternalId());

        order.setShipments(Collections.singletonList(new Shipment(shipment.getType())));
        orderEventsRepository.deleteByOrderIdAndName(orderId, EmailNotificationType.ORDER_SHIPPING.name());
        ordersRepository.save(order);
    }
}
