package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.ParcelTrackingRequest;
import pl.commercelink.shipping.api.ParcelTrackingSubscription;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentTrackingSubscriber {

    static final int MAX_CHECK_ATTEMPTS = 4;
    static final String TRACKING_UNAVAILABLE_EVENT = "SHIPMENT_TRACKING_UNAVAILABLE";
    static final String TRACKING_FAILED_EVENT = "SHIPMENT_TRACKING_FAILED";
    static final String DUPLICATE_TRACKING_NO = "Tracking number is already tracked for another order";
    static final String CHECK_TIMED_OUT = "Furgonetka did not confirm the tracking request in time";
    static final String PROVIDER_UNAVAILABLE = "Shipping provider unavailable";
    static final String RMA_RETRY_UNSUPPORTED = "Tracking confirmation is not retried for RMA shipments";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final StoresRepository storesRepository;
    private final ShippingProviders shippingProviders;
    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final ShipmentTrackingEventPublisher publisher;
    private final OrderEventsRepository orderEventsRepository;
    private final OrdersRepository ordersRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public void subscribe(String storeId, Order order) {
        subscribe(storeId, order.getOrderId(), null, order.getShipments());
    }

    public void subscribe(String storeId, RMA rma) {
        subscribe(storeId, null, rma, rma.getShipments());
    }

    private void subscribe(String storeId, String orderId, RMA rma, List<Shipment> shipments) {
        List<Shipment> candidates = shipments.stream()
                .filter(shipment -> shipment.hasShippingData() && !shipment.hasTrackingSubscription())
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return;
        }
        for (Shipment shipment : candidates) {
            subscribeOne(store, orderId, rma, shipment);
        }
    }

    private void subscribeOne(Store store, String orderId, RMA rma, Shipment shipment) {
        String storeId = store.getStoreId();
        String rmaId = rma == null ? null : rma.getRmaId();
        LocalDateTime now = LocalDateTime.now();
        Optional<Tracker> tracker = trackerFor(store, shipment);
        boolean ownParcel = shipment.getExternalId() != null;
        boolean newlyIndexed = false;
        Optional<ShipmentTracking> existing = shipmentTrackingsRepository.find(storeId, shipment.getTrackingNo());
        if (existing.isPresent()) {
            ShipmentTracking tracking = existing.get();
            boolean sameEntity = (orderId != null && orderId.equals(tracking.getOrderId()))
                    || (rmaId != null && rmaId.equals(tracking.getRmaId()));
            if (!sameEntity) {
                fail(storeId, orderId, shipment, DUPLICATE_TRACKING_NO, now);
                return;
            }
        } else {
            newlyIndexed = shipmentTrackingsRepository.saveIfAbsent(new ShipmentTracking(storeId,
                    shipment.getTrackingNo(), orderId, rmaId, now,
                    ownParcel ? shippingProviders.nameFor(store, shipment) : tracker.map(Tracker::name).orElse(null), ownParcel ? shipment.getExternalId() : null));
            if (!newlyIndexed) {
                fail(storeId, orderId, shipment, DUPLICATE_TRACKING_NO, now);
                return;
            }
        }
        if (tracker.isEmpty()) {
            // recorded once: the index row written above marks the parcel as already reported
            if (newlyIndexed) {
                recordTrackingUnavailable(storeId, orderId, rma, shipment, now);
            }
            return;
        }
        if (ownParcel) {
            shipment.markTrackingActive(shipment.getExternalId());
            return;
        }
        ShippingProvider provider = tracker.get().provider();
        ParcelTrackingSubscription result;
        try {
            result = provider.trackParcel(trackingRequest(shipment, orderId, rmaId));
        } catch (RuntimeException e) {
            if (isRateLimited(e)) {
                // Furgonetka allows 500 add-to-tracking commands per hour: the command was not created,
                // so the delayed re-check repeats the whole request instead of polling a command id
                log.warn("Tracking subscription rate-limited store={} order={} trackingNo={}, retrying later",
                        storeId, orderId, shipment.getTrackingNo());
                result = ParcelTrackingSubscription.pending(null);
            } else {
                fail(storeId, orderId, shipment, e.getMessage(), now);
                return;
            }
        }
        if (result.status() == ParcelTrackingSubscription.Status.PENDING && orderId == null) {
            // RMA shipments come from the shipping provider and are ACTIVE right away; there is no
            // re-check queue for RMA, so a PENDING result must not be left waiting forever
            fail(storeId, null, shipment, RMA_RETRY_UNSUPPORTED, now);
            return;
        }
        apply(storeId, orderId, shipment, result);
        if (result.status() == ParcelTrackingSubscription.Status.PENDING) {
            publisher.publish(new ShipmentTrackingCheckRequest(storeId, orderId, shipment.getTrackingNo()));
        }
    }

    /**
     * A parcel booked through an integration is tracked by that integration; a foreign number (typed in, reported by
     * a supplier) by the store's default integration, the only one that follows parcels it did not create.
     */
    private Optional<Tracker> trackerFor(Store store, Shipment shipment) {
        String name = shipment.getExternalId() != null
                ? shippingProviders.nameFor(store, shipment)
                : store.defaultShippingIntegration();
        if (name == null) {
            return Optional.empty();
        }
        return shippingProviders.forName(store, name)
                .filter(ShippingProvider::supportsParcelTracking)
                .map(provider -> new Tracker(name, provider));
    }

    private record Tracker(String name, ShippingProvider provider) {
    }

    private void recordTrackingUnavailable(String storeId, String orderId, RMA rma, Shipment shipment, LocalDateTime now) {
        log.warn("No integration can track store={} order={} rma={} trackingNo={}: delivery has to be marked by hand",
                storeId, orderId, rma == null ? null : rma.getRmaId(), shipment.getTrackingNo());
        if (orderId != null) {
            orderEventsRepository.save(new OrderEvent(orderId, EventType.action, TRACKING_UNAVAILABLE_EVENT, now));
        } else if (rma != null) {
            // the caller saves this RMA right after subscribing (it saves the tracking marks the same way)
            rma.addEvent(new Event(EventType.action, TRACKING_UNAVAILABLE_EVENT, now));
        }
    }

    /**
     * Delayed re-check of a PENDING subscription (SQS listener). Throws {@link ShipmentTrackingPendingException}
     * to request another delivery while attempts remain; the last attempt always leaves the shipment in a
     * terminal state (ACTIVE or FAILED) so nothing stays PENDING once the message reaches the dead-letter queue.
     */
    public void check(ShipmentTrackingCheckRequest request, int attempt) {
        Order order = ordersRepository.findById(request.getStoreId(), request.getOrderId());
        if (order == null) {
            return;
        }
        Shipment shipment = pendingShipment(order, request.getTrackingNo());
        if (shipment == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ShippingProvider provider = trackingProvider(request.getStoreId());
        ParcelTrackingSubscription result;
        if (provider == null) {
            result = ParcelTrackingSubscription.failed(shipment.getTrackingSubscriptionId(), PROVIDER_UNAVAILABLE);
        } else {
            try {
                result = shipment.getTrackingSubscriptionId() == null
                        ? provider.trackParcel(trackingRequest(shipment, order.getOrderId(), null))
                        : provider.checkParcelTracking(shipment.getTrackingSubscriptionId());
            } catch (RuntimeException e) {
                log.warn("Tracking re-check failed store={} order={} trackingNo={} attempt={}: {}",
                        request.getStoreId(), request.getOrderId(), request.getTrackingNo(), attempt, e.getMessage());
                if (attempt < MAX_CHECK_ATTEMPTS) {
                    throw new ShipmentTrackingPendingException(request.getTrackingNo(), attempt);
                }
                result = ParcelTrackingSubscription.failed(shipment.getTrackingSubscriptionId(), e.getMessage());
            }
        }
        if (result.status() == ParcelTrackingSubscription.Status.PENDING) {
            if (attempt < MAX_CHECK_ATTEMPTS) {
                if (result.subscriptionId() != null
                        && !Objects.equals(result.subscriptionId(), shipment.getTrackingSubscriptionId())) {
                    // a rate-limited subscription has just been accepted: remember the command id for the next check
                    persist(request, result, now);
                }
                throw new ShipmentTrackingPendingException(request.getTrackingNo(), attempt);
            }
            result = ParcelTrackingSubscription.failed(result.subscriptionId(), CHECK_TIMED_OUT);
        }
        boolean applied = persist(request, result, now);
        if (applied && result.status() == ParcelTrackingSubscription.Status.FAILED) {
            orderEventsRepository.save(new OrderEvent(request.getOrderId(), EventType.action, TRACKING_FAILED_EVENT, now));
        }
    }

    // the order may have been edited while the re-check was queued: apply the outcome to the freshly
    // loaded entity and let the executor retry on a version conflict instead of burning a delivery attempt;
    // returns false when the fresh order no longer has that shipment pending (nothing was changed)
    private boolean persist(ShipmentTrackingCheckRequest request, ParcelTrackingSubscription result, LocalDateTime now) {
        AtomicBoolean changed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(request.getStoreId(), request.getOrderId()),
                fresh -> {
                    Shipment target = pendingShipment(fresh, request.getTrackingNo());
                    changed.set(target != null);
                    if (target != null) {
                        apply(request.getStoreId(), request.getOrderId(), target, result);
                    }
                },
                fresh -> {
                    if (changed.get()) {
                        ordersRepository.save(fresh);
                    }
                });
        return changed.get();
    }

    private static Shipment pendingShipment(Order order, String trackingNo) {
        return order.getShipments().stream()
                .filter(s -> s.hasTrackingNo(trackingNo) && s.isTrackingPending())
                .findFirst()
                .orElse(null);
    }

    private ShippingProvider trackingProvider(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return null;
        }
        return shippingProviders.defaultFor(store).filter(ShippingProvider::supportsParcelTracking).orElse(null);
    }

    private static boolean isRateLimited(RuntimeException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof HttpClientException http && http.getStatusCode() == HTTP_TOO_MANY_REQUESTS) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void apply(String storeId, String orderId, Shipment shipment, ParcelTrackingSubscription result) {
        switch (result.status()) {
            case ACTIVE -> shipment.markTrackingActive(result.externalId());
            case PENDING -> shipment.markTrackingPending(result.subscriptionId());
            case FAILED -> {
                logFailure(storeId, orderId, shipment, result.error());
                shipment.markTrackingFailed();
            }
        }
    }

    private void fail(String storeId, String orderId, Shipment shipment, String error, LocalDateTime now) {
        logFailure(storeId, orderId, shipment, error);
        shipment.markTrackingFailed();
        if (orderId != null) {
            orderEventsRepository.save(new OrderEvent(orderId, EventType.action, TRACKING_FAILED_EVENT, now));
        }
    }

    // the failure reason is not persisted on the shipment, so the log is the only place it is kept
    private static void logFailure(String storeId, String orderId, Shipment shipment, String error) {
        log.warn("Tracking subscription failed store={} order={} trackingNo={}: {}",
                storeId, orderId, shipment.getTrackingNo(), error);
    }

    private static ParcelTrackingRequest trackingRequest(Shipment shipment, String orderId, String rmaId) {
        return new ParcelTrackingRequest(shipment.getTrackingNo(), shipment.getCarrier(), label(orderId, rmaId));
    }

    private static String label(String orderId, String rmaId) {
        return orderId != null ? "CommerceLink order " + orderId : "CommerceLink RMA " + rmaId;
    }
}
