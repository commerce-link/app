package pl.commercelink.shipping;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingWebhookRequest;
import pl.commercelink.shipping.api.ShippingWebhookResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.web.dtos.StatusDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/Store/{storeId}/Webhooks/Shipping")
public class ShippingWebhookController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderLifecycle orderLifecycle;

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private GoodsOutEventPublisher goodsOutEventPublisher;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private ShippingProviderFactory shippingProviderFactory;

    @PostMapping("/{providerName}")
    @ResponseBody
    public StatusDto receiveWebhook(
            @PathVariable("storeId") String storeId,
            @PathVariable("providerName") String providerName,
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers
    ) {
        String normalizedName = providerName.toLowerCase();

        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new RuntimeException("Internal error.");
        }

        ShippingProvider provider = shippingProviderFactory.get(store);
        ShippingWebhookResult result = provider.processWebhook(new ShippingWebhookRequest(payload, headers));

        Optional<Order> order = findOrderByTrackingNo(storeId, result.trackingNo());
        if (order.isPresent()) {
            handleOrderShipmentStatusChange(order.get(), result);
        } else {
            findRmaByTrackingNo(storeId, result.trackingNo())
                    .ifPresent(rma -> handleRmaShipmentStatusChange(rma, result));
        }

        return new StatusDto("OK");
    }

    private void handleOrderShipmentStatusChange(Order order, ShippingWebhookResult result) {
        if (result.state() == ShippingWebhookResult.ShipmentState.COLLECTED) {
            orderEventsRepository.save(new OrderEvent(order.getOrderId(), EventType.action, "SHIPMENT_COLLECTED", result.datetime()));
            goodsOutEventPublisher.publish(order, "System");
        }

        if (result.state() == ShippingWebhookResult.ShipmentState.DELIVERED) {
            orderEventsRepository.save(new OrderEvent(order.getOrderId(), EventType.action, "SHIPMENT_DELIVERED", result.datetime()));
            order.getShipments().stream()
                    .filter(s -> s.hasTrackingNo(result.trackingNo()))
                    .forEach(s -> s.setDeliveredAt(result.datetime()));
            orderLifecycle.update(order);
        }
    }

    private void handleRmaShipmentStatusChange(RMA rma, ShippingWebhookResult result) {
        if (result.state() == ShippingWebhookResult.ShipmentState.DELIVERED) {
            rma.getShipments().stream()
                    .filter(s -> s.hasTrackingNo(result.trackingNo()))
                    .forEach(s -> s.setDeliveredAt(result.datetime()));
        }

        if (rma.getShipments().stream().allMatch(s -> s.getDeliveredAt() != null)) {
            rma.setStatus(RMAStatus.ItemsReceived);
        }

        rmaRepository.save(rma);
    }

    private Optional<Order> findOrderByTrackingNo(String storeId, String trackingNo) {
        List<Order> orders = ordersRepository.findAllByStoreIdAndStatus(storeId, OrderStatus.Shipping);
        return orders.stream()
                .filter(o -> o.getShipments().stream().anyMatch(s -> s.hasTrackingNo(trackingNo)))
                .findFirst();
    }

    private Optional<RMA> findRmaByTrackingNo(String storeId, String trackingNo) {
        List<RMA> rmaList = rmaRepository.findAllByStoreIdAndStatus(storeId, RMAStatus.WaitingForItems);
        return rmaList.stream()
                .filter(r -> r.getShipments().stream().anyMatch(s -> s.hasTrackingNo(trackingNo)))
                .findFirst();
    }
}
