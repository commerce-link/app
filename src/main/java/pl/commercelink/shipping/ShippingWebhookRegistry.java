package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;
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
import pl.commercelink.shipping.api.ShippingWebhookResult;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.util.List;
import java.util.Optional;

@Configuration
@Slf4j
public class ShippingWebhookRegistry {

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderLifecycle orderLifecycle;
    private final RMARepository rmaRepository;
    private final GoodsOutEventPublisher goodsOutEventPublisher;
    private final OrderEventsRepository orderEventsRepository;
    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final RouterFunction<ServerResponse> routes;

    ShippingWebhookRegistry(ShippingProviderFactory shippingProviderFactory,
                            StoresRepository storesRepository,
                            OrdersRepository ordersRepository,
                            OrderLifecycle orderLifecycle,
                            RMARepository rmaRepository,
                            GoodsOutEventPublisher goodsOutEventPublisher,
                            OrderEventsRepository orderEventsRepository,
                            ShipmentTrackingsRepository shipmentTrackingsRepository,
                            OptimisticLockingExecutor optimisticLockingExecutor) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.orderLifecycle = orderLifecycle;
        this.rmaRepository = rmaRepository;
        this.goodsOutEventPublisher = goodsOutEventPublisher;
        this.orderEventsRepository = orderEventsRepository;
        this.shipmentTrackingsRepository = shipmentTrackingsRepository;
        this.optimisticLockingExecutor = optimisticLockingExecutor;

        this.routes = EventBindingRegistrar.forDescriptors(shippingProviderFactory.availableProviders())
                .<ShippingWebhookResult>withWebhooks(
                        "/Store/{storeId}/Webhooks/Shipping/",
                        (descriptor, storeId) -> shippingProviderFactory.loadConfiguration(
                                storesRepository.findById(storeId), descriptor.name()),
                        (descriptor, storeId, result) -> processResult(storeId, result))
                .register();
    }

    @Bean
    RouterFunction<ServerResponse> shippingWebhookRoutes() {
        return routes;
    }

    private void processResult(String storeId, ShippingWebhookResult result) {
        if (storesRepository.findById(storeId) == null) {
            throw new RuntimeException("Internal error.");
        }
        Optional<ShipmentTracking> tracking = shipmentTrackingsRepository.find(storeId, result.trackingNo());
        if (tracking.isEmpty()) {
            log.info("Shipping webhook ignored: no tracked shipment for store={} trackingNo={} state={}",
                    storeId, result.trackingNo(), result.state());
            return;
        }
        if (tracking.get().getOrderId() != null) {
            Order order = ordersRepository.findById(storeId, tracking.get().getOrderId());
            if (order != null) {
                handleOrderShipmentStatusChange(order, result);
            }
            return;
        }
        if (tracking.get().getRmaId() != null) {
            RMA rma = rmaRepository.findById(storeId, tracking.get().getRmaId());
            if (rma != null) {
                handleRmaShipmentStatusChange(rma, result);
            }
        }
    }

    private void handleOrderShipmentStatusChange(Order order, ShippingWebhookResult result) {
        if (result.state() == ShippingWebhookResult.ShipmentState.COLLECTED) {
            if (order.getStatus().isOneOf(OrderStatus.Shipping, OrderStatus.Delivered, OrderStatus.Completed)) {
                orderEventsRepository.save(new OrderEvent(order.getOrderId(), EventType.action, "SHIPMENT_COLLECTED", result.datetime()));
                goodsOutEventPublisher.publish(order, "System");
            } else {
                log.info("Shipping webhook COLLECTED ignored: order={} status={}", order.getOrderId(), order.getStatus());
            }
        }

        if (result.state() == ShippingWebhookResult.ShipmentState.DELIVERED) {
            String storeId = order.getStoreId();
            String orderId = order.getOrderId();
            boolean matched = optimisticLockingExecutor.modifyAndSaveReturning(
                    () -> ordersRepository.findById(storeId, orderId),
                    fresh -> {
                        List<Shipment> delivered = fresh.getShipments().stream()
                                .filter(s -> s.hasTrackingNo(result.trackingNo()))
                                .toList();
                        delivered.forEach(s -> s.setDeliveredAt(result.datetime()));
                        return !delivered.isEmpty();
                    },
                    orderLifecycle::update
            );
            // a stale index row (tracking number edited afterwards) must not add a misleading timeline entry
            if (matched) {
                orderEventsRepository.save(new OrderEvent(orderId, EventType.action, "SHIPMENT_DELIVERED", result.datetime()));
            } else {
                log.info("Shipping webhook DELIVERED matched no shipment: order={} trackingNo={} (index row stale?)",
                        orderId, result.trackingNo());
            }
        }
    }

    private void handleRmaShipmentStatusChange(RMA rma, ShippingWebhookResult result) {
        if (rma.getStatus() != RMAStatus.WaitingForItems) {
            log.info("Shipping webhook ignored: rma={} status={}", rma.getRmaId(), rma.getStatus());
            return;
        }

        if (result.state() == ShippingWebhookResult.ShipmentState.DELIVERED) {
            String storeId = rma.getStoreId();
            String rmaId = rma.getRmaId();
            optimisticLockingExecutor.modifyAndSave(
                    () -> rmaRepository.findById(storeId, rmaId),
                    fresh -> {
                        fresh.getShipments().stream()
                                .filter(s -> s.hasTrackingNo(result.trackingNo()))
                                .forEach(s -> s.setDeliveredAt(result.datetime()));
                        if (fresh.getShipments().stream().allMatch(s -> s.getDeliveredAt() != null)) {
                            fresh.setStatus(RMAStatus.ItemsReceived);
                        }
                    },
                    rmaRepository::save
            );
        }
    }
}
