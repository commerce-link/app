package pl.commercelink.shipping;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingWebhookRequest;
import pl.commercelink.shipping.api.ShippingWebhookResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
public class ShippingWebhookRegistry {

    private final ShippingProviderFactory shippingProviderFactory;
    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderLifecycle orderLifecycle;
    private final RMARepository rmaRepository;
    private final GoodsOutEventPublisher goodsOutEventPublisher;
    private final OrderEventsRepository orderEventsRepository;
    private final RouterFunction<ServerResponse> routes;

    ShippingWebhookRegistry(ShippingProviderFactory shippingProviderFactory,
                            StoresRepository storesRepository,
                            OrdersRepository ordersRepository,
                            OrderLifecycle orderLifecycle,
                            RMARepository rmaRepository,
                            GoodsOutEventPublisher goodsOutEventPublisher,
                            OrderEventsRepository orderEventsRepository) {
        this.shippingProviderFactory = shippingProviderFactory;
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.orderLifecycle = orderLifecycle;
        this.rmaRepository = rmaRepository;
        this.goodsOutEventPublisher = goodsOutEventPublisher;
        this.orderEventsRepository = orderEventsRepository;

        this.routes = EventBindingRegistrar.forDescriptors(shippingProviderFactory.availableProviders())
                .withWebhooks("/Store/{storeId}/Webhooks/Shipping/", descriptor ->
                        (event, storeId, headers) -> processShipping((String) event, headers, storeId, descriptor.name()))
                .register();
    }

    @Bean
    RouterFunction<ServerResponse> shippingWebhookRoutes() {
        return routes;
    }

    private void processShipping(String payload, Map<String, String> headers,
                                 String storeId, String providerName) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new RuntimeException("Internal error.");
        }

        ShippingProvider provider = shippingProviderFactory.get(store, providerName);
        ShippingWebhookResult result = provider.processWebhook(new ShippingWebhookRequest(payload, headers));

        Optional<Order> order = findOrderByTrackingNo(storeId, result.trackingNo());
        if (order.isPresent()) {
            handleOrderShipmentStatusChange(order.get(), result);
        } else {
            findRmaByTrackingNo(storeId, result.trackingNo())
                    .ifPresent(rma -> handleRmaShipmentStatusChange(rma, result));
        }
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
