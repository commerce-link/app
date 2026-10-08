package pl.commercelink.shipping.tracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.shipping.ShipmentTrackingsRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The one path a carrier status takes into an order or an RMA, whether it came from a webhook or from polling.
 * The state reached is written to the ShipmentTrackings row first and conditionally, so a status repeated by the
 * carrier, by the hourly poll or by a second instance takes effect once; effects that fail after that write are
 * lost rather than repeated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentTrackingUpdates {

    static final String EVENT_COLLECTED = "SHIPMENT_COLLECTED";
    static final String EVENT_DELIVERED = "SHIPMENT_DELIVERED";
    public static final String EVENT_EXPIRED = "SHIPMENT_TRACKING_EXPIRED";

    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final OrdersRepository ordersRepository;
    private final OrderLifecycle orderLifecycle;
    private final RMARepository rmaRepository;
    private final GoodsOutEventPublisher goodsOutEventPublisher;
    private final OrderEventsRepository orderEventsRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public boolean apply(String storeId, String trackingNo, ShipmentTrackingState state) {
        return apply(storeId, trackingNo, state, LocalDateTime.now());
    }

    /** @return true when this call moved the parcel to {@code state} and carried out its effects */
    public boolean apply(String storeId, String trackingNo, ShipmentTrackingState state, LocalDateTime occurredAt) {
        Optional<ShipmentTracking> found = shipmentTrackingsRepository.find(storeId, trackingNo);
        if (found.isEmpty()) {
            log.warn("Shipment status ignored: no tracked shipment for store={} trackingNo={} state={}",
                    storeId, trackingNo, state);
            return false;
        }
        ShipmentTracking row = found.get();
        if (row.hasUnknownState()) {
            log.warn("Shipment status ignored: store={} trackingNo={} has state {} unknown to this version (got {})",
                    storeId, trackingNo, row.getState(), state);
            return false;
        }
        if (!ShipmentTrackingState.isForward(row.currentState(), state)) {
            log.debug("Shipment status ignored: store={} trackingNo={} already {} (got {})",
                    storeId, trackingNo, row.getState(), state);
            return false;
        }
        if (row.getOrderId() != null) {
            return applyToOrder(row, state, occurredAt);
        }
        if (row.getRmaId() != null) {
            return applyToRma(row, state, occurredAt);
        }
        return false;
    }

    private boolean applyToOrder(ShipmentTracking row, ShipmentTrackingState state, LocalDateTime occurredAt) {
        Order order = ordersRepository.findById(row.getStoreId(), row.getOrderId());
        if (order == null) {
            return false;
        }
        if (state == ShipmentTrackingState.COLLECTED
                && !order.getStatus().isOneOf(OrderStatus.Shipping, OrderStatus.Delivered, OrderStatus.Completed)) {
            // not remembered: a later webhook or poll applies it once the order ships
            log.warn("Shipment COLLECTED not applied yet: order={} status={}", order.getOrderId(), order.getStatus());
            return false;
        }
        String previousState = row.getState();
        if (!shipmentTrackingsRepository.advance(row, state)) {
            log.warn("Shipment status {} already applied by another writer: store={} trackingNo={}",
                    state, row.getStoreId(), row.getTrackingNo());
            return false;
        }
        try {
            return applyOrderEffects(order, row, state, occurredAt);
        } catch (RuntimeException e) {
            revertAfterFailedEffects(row, previousState, state, e);
            throw e;
        }
    }

    private boolean applyOrderEffects(Order order, ShipmentTracking row, ShipmentTrackingState state, LocalDateTime occurredAt) {
        String orderId = order.getOrderId();
        switch (state) {
            case COLLECTED -> {
                orderEventsRepository.save(new OrderEvent(orderId, EventType.action, EVENT_COLLECTED, occurredAt));
                goodsOutEventPublisher.publish(order, "System");
                return true;
            }
            case DELIVERED -> {
                return markOrderDelivered(order.getStoreId(), orderId, row.getTrackingNo(), occurredAt);
            }
            case EXPIRED -> {
                orderEventsRepository.save(new OrderEvent(orderId, EventType.action, EVENT_EXPIRED, occurredAt));
                return true;
            }
        }
        return false;
    }

    private boolean markOrderDelivered(String storeId, String orderId, String trackingNo, LocalDateTime deliveredAt) {
        boolean matched = optimisticLockingExecutor.modifyAndSaveReturning(
                () -> ordersRepository.findById(storeId, orderId),
                fresh -> {
                    List<Shipment> delivered = fresh.getShipments().stream()
                            .filter(s -> s.hasTrackingNo(trackingNo))
                            .toList();
                    delivered.forEach(s -> s.setDeliveredAt(deliveredAt));
                    return !delivered.isEmpty();
                },
                orderLifecycle::update
        );
        // a stale index row (tracking number edited afterwards) must not add a misleading timeline entry
        if (matched) {
            orderEventsRepository.save(new OrderEvent(orderId, EventType.action, EVENT_DELIVERED, deliveredAt));
        } else {
            log.warn("Shipment DELIVERED matched no shipment: order={} trackingNo={} (index row stale?)", orderId, trackingNo);
        }
        return matched;
    }

    private boolean applyToRma(ShipmentTracking row, ShipmentTrackingState state, LocalDateTime occurredAt) {
        RMA rma = rmaRepository.findById(row.getStoreId(), row.getRmaId());
        if (rma == null) {
            return false;
        }
        if (state == ShipmentTrackingState.DELIVERED && rma.getStatus() != RMAStatus.WaitingForItems) {
            log.warn("Shipment DELIVERED ignored: rma={} status={}", rma.getRmaId(), rma.getStatus());
            return false;
        }
        String previousState = row.getState();
        if (!shipmentTrackingsRepository.advance(row, state)) {
            return false;
        }
        try {
            return applyRmaEffects(rma, row, state, occurredAt);
        } catch (RuntimeException e) {
            revertAfterFailedEffects(row, previousState, state, e);
            throw e;
        }
    }

    private boolean applyRmaEffects(RMA rma, ShipmentTracking row, ShipmentTrackingState state, LocalDateTime occurredAt) {
        String storeId = rma.getStoreId();
        String rmaId = rma.getRmaId();
        switch (state) {
            case COLLECTED -> {
                // an RMA has no collected effect (the webhook never had one); the state only stops repeats
            }
            case DELIVERED -> optimisticLockingExecutor.modifyAndSave(
                    () -> rmaRepository.findById(storeId, rmaId),
                    fresh -> {
                        fresh.getShipments().stream()
                                .filter(s -> s.hasTrackingNo(row.getTrackingNo()))
                                .forEach(s -> s.setDeliveredAt(occurredAt));
                        if (fresh.getShipments().stream().allMatch(s -> s.getDeliveredAt() != null)) {
                            fresh.setStatus(RMAStatus.ItemsReceived);
                        }
                    },
                    rmaRepository::save
            );
            case EXPIRED -> optimisticLockingExecutor.modifyAndSave(
                    () -> rmaRepository.findById(storeId, rmaId),
                    fresh -> fresh.addEvent(new Event(EventType.action, EVENT_EXPIRED, occurredAt)),
                    rmaRepository::save
            );
        }
        return true;
    }

    /**
     * The state is written before the effects so that a status never takes effect twice. When the effects then fail,
     * the state is put back (only if still ours), so that the next poll or webhook retry applies the status again.
     */
    private void revertAfterFailedEffects(ShipmentTracking row, String previousState, ShipmentTrackingState state,
                                          RuntimeException cause) {
        boolean reverted;
        try {
            reverted = shipmentTrackingsRepository.revert(row, previousState);
        } catch (RuntimeException revertFailure) {
            cause.addSuppressed(revertFailure);
            reverted = false;
        }
        if (reverted) {
            log.error("Carrier status {} could not be applied, it will be applied again on the next poll or webhook: store={} trackingNo={}",
                    state, row.getStoreId(), row.getTrackingNo(), cause);
        } else {
            log.error("Carrier status {} could not be applied and the state could not be reverted, the status is LOST, mark the delivery by hand: store={} trackingNo={}",
                    state, row.getStoreId(), row.getTrackingNo(), cause);
        }
    }
}
