package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLookup;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.util.OperationResult;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class DropshipTrackingService {

    static final String TRACKING_APPLIED_EVENT = "DROPSHIP_TRACKING_APPLIED";
    static final String TRACKING_UNSUPPORTED_EVENT = "DROPSHIP_TRACKING_UNSUPPORTED";
    static final String TRACKING_NO_DATA_EVENT = "DROPSHIP_TRACKING_NO_DATA";
    static final String SUPPLIER_CANCELLED_EVENT = "DROPSHIP_SUPPLIER_CANCELLED";
    static final String TRACKING_GIVEN_UP_EVENT = "DROPSHIP_TRACKING_GIVEN_UP";
    private static final String ORDER_CANCELLED_MESSAGE = "deliveries.dropship.shipment.error.orderCancelled";

    enum TrackingOutcome { SKIPPED, UNSUPPORTED, PROCESSING, APPLIED, CANCELLED, NO_DATA, GIVEN_UP, ERROR }

    private final DeliveriesQueryService deliveriesQueryService;
    private final DeliveriesRepository deliveriesRepository;
    private final OrdersRepository ordersRepository;
    private final SupplierProviderResolver providerResolver;
    private final DropshipDeliveryCompletion completion;
    private final DropshipTrackingProperties properties;
    private final Clock clock;

    @Autowired
    public DropshipTrackingService(DeliveriesQueryService deliveriesQueryService,
                                   DeliveriesRepository deliveriesRepository, OrdersRepository ordersRepository,
                                   SupplierProviderResolver providerResolver, DropshipDeliveryCompletion completion,
                                   DropshipTrackingProperties properties) {
        this(deliveriesQueryService, deliveriesRepository, ordersRepository, providerResolver, completion, properties,
                Clock.systemDefaultZone());
    }

    DropshipTrackingService(DeliveriesQueryService deliveriesQueryService, DeliveriesRepository deliveriesRepository,
                            OrdersRepository ordersRepository, SupplierProviderResolver providerResolver,
                            DropshipDeliveryCompletion completion, DropshipTrackingProperties properties, Clock clock) {
        this.deliveriesQueryService = deliveriesQueryService;
        this.deliveriesRepository = deliveriesRepository;
        this.ordersRepository = ordersRepository;
        this.providerResolver = providerResolver;
        this.completion = completion;
        this.properties = properties;
        this.clock = clock;
    }

    public void check(String storeId, String deliveryId) {
        check(storeId, deliveryId, false);
    }

    public ManualTrackingOutcome checkManually(String storeId, String deliveryId) {
        try {
            return switch (check(storeId, deliveryId, true)) {
                case APPLIED -> ManualTrackingOutcome.CONFIRMED;
                case PROCESSING -> ManualTrackingOutcome.STILL_PROCESSING;
                case CANCELLED -> ManualTrackingOutcome.CANCELLED;
                case NO_DATA -> ManualTrackingOutcome.NO_DATA;
                case SKIPPED, UNSUPPORTED, GIVEN_UP, ERROR -> ManualTrackingOutcome.UNAVAILABLE;
            };
        } catch (RuntimeException e) {
            log.error("Manual dropship tracking check failed: store={} delivery={}", storeId, deliveryId, e);
            return ManualTrackingOutcome.UNAVAILABLE;
        }
    }

    TrackingOutcome check(String storeId, String deliveryId, boolean manual) {
        Delivery delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery == null || !delivery.isTrackable()) {
            return TrackingOutcome.SKIPPED;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (manual) {
            if (delivery.getTrackingView().getState() == DeliveryTrackingState.UNSUPPORTED) {
                return TrackingOutcome.SKIPPED;
            }
        } else if (!delivery.isTrackingPending() || isNotDue(delivery, now)) {
            return TrackingOutcome.SKIPPED;
        }

        SupplierProvider provider;
        try {
            provider = providerResolver.resolve(storeId, delivery.getProvider());
        } catch (RuntimeException e) {
            return recordError(delivery, now, manual, e);
        }
        if (provider == null) {
            return recordError(delivery, now, manual,
                    new IllegalStateException("No supplier connection for " + delivery.getProvider()));
        }
        if (!provider.supportsOrderTracking()) {
            finish(delivery, DeliveryTrackingState.UNSUPPORTED, TRACKING_UNSUPPORTED_EVENT, now);
            log.info("Dropship tracking unsupported: store={} delivery={} provider={}",
                    storeId, deliveryId, delivery.getProvider());
            return TrackingOutcome.UNSUPPORTED;
        }

        Optional<SupplierOrderTracking> tracking;
        try {
            tracking = provider.trackOrder(new SupplierOrderLookup(delivery.getExternalDeliveryId(), delivery.getPurchaseRef()));
        } catch (SupplierOrderException e) {
            return recordError(delivery, now, manual, e);
        }
        delivery.tracking().recordCheck(now);
        if (tracking.isEmpty()) {
            return scheduleNext(delivery, now, manual);
        }
        SupplierOrderTracking snapshot = tracking.get();
        return switch (snapshot.state()) {
            case PROCESSING -> scheduleNext(delivery, now, manual);
            case CANCELLED -> {
                finish(delivery, DeliveryTrackingState.CANCELLED_BY_SUPPLIER, SUPPLIER_CANCELLED_EVENT, now);
                log.error("Supplier cancelled dropship order: store={} delivery={} provider={} externalOrderId={}",
                        storeId, deliveryId, delivery.getProvider(), delivery.getExternalDeliveryId());
                yield TrackingOutcome.CANCELLED;
            }
            case PARTIALLY_SHIPPED, SHIPPED -> snapshot.parcels().isEmpty()
                    ? shippedWithoutParcels(delivery, snapshot, now, manual)
                    : applyParcels(storeId, delivery, snapshot, now, manual);
        };
    }

    private TrackingOutcome shippedWithoutParcels(Delivery delivery, SupplierOrderTracking snapshot,
                                                  LocalDateTime now, boolean manual) {
        if (snapshot.state() == SupplierOrderState.PARTIALLY_SHIPPED) {
            return scheduleNext(delivery, now, manual);
        }
        finish(delivery, DeliveryTrackingState.SHIPPED_WITHOUT_DATA, TRACKING_NO_DATA_EVENT, now);
        log.error("Supplier reports dropship order shipped without parcel data: store={} delivery={} provider={} externalOrderId={}",
                delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider(), delivery.getExternalDeliveryId());
        return TrackingOutcome.NO_DATA;
    }

    private TrackingOutcome applyParcels(String storeId, Delivery delivery, SupplierOrderTracking snapshot,
                                         LocalDateTime now, boolean manual) {
        Order order = orderOf(storeId, delivery);
        // A customer who chose a parcel locker gets a PickupPoint shipment carrying the point they picked;
        // the supplier only reports the carrier and the waybill.
        Shipment pickup = order == null ? null : DropshipPurchaseService.pickupShipment(order).orElse(null);
        List<Allocation> open = delivery.getAllocations().stream()
                .filter(allocation -> allocation.getType() == AllocationType.Order && allocation.isInAllocation())
                .toList();
        // one shipment per tracking number: a supplier listing one row per physical package under the same
        // label must not add the same shipment twice, so only the first row of a number is applied
        Map<String, SupplierParcel> firstByTrackingNo = new LinkedHashMap<>();
        for (SupplierParcel parcel : snapshot.parcels()) {
            firstByTrackingNo.putIfAbsent(parcel.trackingNo(), parcel);
        }
        List<SupplierParcel> pending = firstByTrackingNo.values().stream()
                .filter(parcel -> order == null
                        || order.getShipments().stream().noneMatch(shipment -> shipment.hasTrackingNo(parcel.trackingNo())))
                .toList();
        int applied = 0;
        for (int i = 0; i < pending.size(); i++) {
            SupplierParcel parcel = pending.get(i);
            boolean absorbRemaining = snapshot.state() == SupplierOrderState.SHIPPED && i == pending.size() - 1;
            List<Allocation> selected = DropshipParcelMatcher.select(parcel, open, absorbRemaining);
            if (selected.isEmpty()) {
                continue;
            }
            Set<Allocation> taken = Collections.newSetFromMap(new IdentityHashMap<>());
            taken.addAll(selected);
            List<Allocation> remaining = open.stream().filter(allocation -> !taken.contains(allocation)).toList();
            // The supplier's parcel carrier overwrites the customer-chosen carrier on the reused PickupPoint
            // shipment (e.g. DPD delivering to an InPost point): the waybill carrier is what marketplaces need,
            // while the point code stays the customer's own.
            ShipmentType type = pickup != null ? ShipmentType.PickupPoint : ShipmentType.Courier;
            String collectionPointCode = pickup != null ? pickup.getCollectionPointCode() : null;
            LocalDateTime shippedAt = parcel.shippedAt() != null ? parcel.shippedAt() : now;
            DropshipShipment shipment = new DropshipShipment(type, parcel.carrier(), parcel.trackingNo(),
                    collectionPointCode, shippedAt, parcel.trackingUrl());
            String invalidShipment = shipment.validationError();
            if (invalidShipment != null) {
                delivery.tracking().finish(DeliveryTrackingState.SHIPPED_WITHOUT_DATA,
                        "Supplier parcel cannot be applied: " + invalidShipment);
                finish(delivery, TRACKING_NO_DATA_EVENT, now);
                log.error("Supplier parcel failed validation, dropship delivery finished as shipped without data: "
                                + "store={} delivery={} provider={} externalOrderId={} error={}",
                        storeId, delivery.getDeliveryId(), delivery.getProvider(), delivery.getExternalDeliveryId(),
                        invalidShipment);
                return TrackingOutcome.NO_DATA;
            }
            OperationResult<DropshipShipmentResult> result = completion.confirmShipped(storeId, delivery, selected, remaining, shipment,
                    (d, r) -> {
                        d.addEvent(new Event(EventType.action, TRACKING_APPLIED_EVENT, now));
                        if (r == DropshipShipmentResult.COMPLETED) {
                            d.tracking().finish(DeliveryTrackingState.COMPLETED);
                        } else {
                            d.tracking().scheduleNext(now.plus(properties.intervalFor(ageOf(d, now))));
                        }
                    });
            if (!result.isSuccess()) {
                if (ORDER_CANCELLED_MESSAGE.equals(result.getMessage())) {
                    finish(delivery, DeliveryTrackingState.GIVEN_UP, TRACKING_GIVEN_UP_EVENT, now);
                    log.error("Dropship tracking stopped, order cancelled: store={} delivery={}", storeId, delivery.getDeliveryId());
                    return TrackingOutcome.GIVEN_UP;
                }
                continue;
            }
            applied++;
            open = remaining;
            log.info("Dropship parcel applied: store={} delivery={} provider={} externalOrderId={} trackingNo={} result={}",
                    storeId, delivery.getDeliveryId(), delivery.getProvider(), delivery.getExternalDeliveryId(),
                    parcel.trackingNo(), result.getPayload());
            if (result.getPayload() == DropshipShipmentResult.COMPLETED) {
                return TrackingOutcome.APPLIED;
            }
        }
        if (applied == 0) {
            if (alreadyFulfilled(snapshot, delivery, open, pending)) {
                return completeAlreadyConfirmed(delivery, now);
            }
            return scheduleNext(delivery, now, manual);
        }
        return TrackingOutcome.APPLIED;
    }

    /**
     * Every parcel the supplier reports is already on the order and no allocation is left open: the order side of an
     * earlier confirmation was committed while the delivery write was lost (version conflict, crash before the save).
     * A delivery without any order allocation is a different case — the operator removed them — and keeps polling.
     */
    private static boolean alreadyFulfilled(SupplierOrderTracking snapshot, Delivery delivery,
                                            List<Allocation> open, List<SupplierParcel> pending) {
        return snapshot.state() == SupplierOrderState.SHIPPED
                && pending.isEmpty()
                && open.isEmpty()
                && delivery.getAllocations().stream().anyMatch(allocation -> allocation.getType() == AllocationType.Order);
    }

    private TrackingOutcome completeAlreadyConfirmed(Delivery delivery, LocalDateTime now) {
        delivery.markAsReceived();
        finish(delivery, DeliveryTrackingState.COMPLETED, TRACKING_APPLIED_EVENT, now);
        log.warn("Dropship delivery reconciled with the already shipped order: store={} delivery={} provider={} externalOrderId={}",
                delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider(), delivery.getExternalDeliveryId());
        return TrackingOutcome.APPLIED;
    }

    private Order orderOf(String storeId, Delivery delivery) {
        return delivery.getAllocations().stream()
                .filter(allocation -> allocation.getType() == AllocationType.Order)
                .map(allocation -> allocation.getKey().getOrderId())
                .findFirst()
                .map(orderId -> ordersRepository.findById(storeId, orderId))
                .orElse(null);
    }

    private TrackingOutcome recordError(Delivery delivery, LocalDateTime now, boolean manual, RuntimeException e) {
        DeliveryTracking tracking = delivery.tracking();
        tracking.recordError(e.getMessage(), now, !manual);
        if (!manual && tracking.isExhausted(properties.maxConsecutiveErrors())) {
            finish(delivery, DeliveryTrackingState.GIVEN_UP, TRACKING_GIVEN_UP_EVENT, now);
            log.error("Dropship tracking given up after {} consecutive errors: store={} delivery={} provider={}",
                    tracking.getConsecutiveErrors(), delivery.getStoreId(), delivery.getDeliveryId(),
                    delivery.getProvider(), e);
            return TrackingOutcome.GIVEN_UP;
        }
        tracking.scheduleNext(now.plus(properties.intervalFor(ageOf(delivery, now))));
        deliveriesRepository.save(delivery);
        log.warn("Dropship tracking check failed: store={} delivery={} provider={} error={}",
                delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider(), e.getMessage());
        return TrackingOutcome.ERROR;
    }

    private TrackingOutcome scheduleNext(Delivery delivery, LocalDateTime now, boolean manual) {
        if (!manual && ageOf(delivery, now).compareTo(properties.maxAge()) > 0) {
            finish(delivery, DeliveryTrackingState.GIVEN_UP, TRACKING_GIVEN_UP_EVENT, now);
            log.error("Dropship tracking given up, order not shipped within {}: store={} delivery={} provider={}",
                    properties.maxAge(), delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider());
            return TrackingOutcome.GIVEN_UP;
        }
        delivery.tracking().scheduleNext(now.plus(properties.intervalFor(ageOf(delivery, now))));
        deliveriesRepository.save(delivery);
        return TrackingOutcome.PROCESSING;
    }

    private void finish(Delivery delivery, DeliveryTrackingState state, String eventName, LocalDateTime now) {
        delivery.tracking().finish(state);
        finish(delivery, eventName, now);
    }

    private void finish(Delivery delivery, String eventName, LocalDateTime now) {
        delivery.addEvent(new Event(EventType.action, eventName, now));
        deliveriesRepository.save(delivery);
    }

    private static boolean isNotDue(Delivery delivery, LocalDateTime now) {
        return !delivery.getTrackingView().isDue(now);
    }

    private static Duration ageOf(Delivery delivery, LocalDateTime now) {
        return delivery.getOrderedAt() == null ? Duration.ofDays(1) : Duration.between(delivery.getOrderedAt(), now);
    }
}
