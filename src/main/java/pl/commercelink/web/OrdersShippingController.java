package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.commercelink.orders.*;
import pl.commercelink.shipping.AbstractShippingController;

import java.util.Collections;
import java.util.List;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.orders.Shipment;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

@Controller
@RequestMapping("/dashboard/orders/{orderId}/shipping")
public class OrdersShippingController extends AbstractShippingController {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderLifecycle orderLifecycle;

    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;

    @GetMapping("")
    public String initiate(@PathVariable("orderId") String orderId, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        if (order.getShipments().stream().allMatch(Shipment::hasShippingData)) {
            throw new RuntimeException("All shipments have been defined already");
        }
        ShippingForm form = new ShippingForm(orderId, "orders");
        return renderShippingForm(getStore(), form, Collections.singletonList(order.getShippingDetails()), model);
    }

    @Override
    protected double calculateShippingInsurance(ShippingForm form) {
        Order order = ordersRepository.findById(getStoreId(), form.getShippingEntityId());
        return order.getTotalPrice();
    }

    @Override
    protected List<ShippingDetails> retrieveShippingDetailsList(ShippingForm form) {
        Order order = ordersRepository.findById(getStoreId(), form.getShippingEntityId());
        return Collections.singletonList(order.getShippingDetails());
    }

    @Override
    protected DeliveryTarget resolveDeliveryTarget(ShippingForm form) {
        Order order = ordersRepository.findById(getStoreId(), form.getShippingEntityId());
        String shippingProvider = getStore().getConfigurationValue(IntegrationType.SHIPPING_PROVIDER);
        return order.firstShipment()
                .map(shipment -> new DeliveryTarget(shippingProvider, shipment.getCarrier(),
                        shipment.getCollectionPointCode()))
                .orElseGet(() -> new DeliveryTarget(null, null, null));
    }

    @Override
    protected void onShippingCreated(ShippingForm form, List<Shipment> shipments) {
        Order order = ordersRepository.findById(getStoreId(), form.getShippingEntityId());

        order.replaceShipments(shipments);

        orderLifecycle.update(order);
        orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.ShipmentCreated);
    }
}
