package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentTrackingSubscriber {

    static final int MAX_CHECK_ATTEMPTS = 4;
    static final String TRACKING_FAILED_EVENT = "SHIPMENT_TRACKING_FAILED";
    static final String DUPLICATE_TRACKING_NO = "Tracking number is already tracked for another order";
    static final String CHECK_TIMED_OUT = "Furgonetka did not confirm the tracking request in time";
    static final String PROVIDER_UNAVAILABLE = "Shipping provider unavailable";
    static final String RMA_RETRY_UNSUPPORTED = "Tracking confirmation is not retried for RMA shipments";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final StoresRepository storesRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final ShipmentTrackingEventPublisher publisher;
    private final OrderEventsRepository orderEventsRepository;
    private final OrdersRepository ordersRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public void subscribe(String storeId, Order order) {
        subscribe(storeId, order.getOrderId(), null, order.getShipments());
    }

    public void subscribe(String storeId, RMA rma) {
        subscribe(storeId, null, rma.getRmaId(), rma.getShipments());
    }

    private void subscribe(String storeId, String orderId, String rmaId, List<Shipment> shipments) {
        List<Shipment> candidates = shipments.stream()
                .filter(shipment -> shipment.hasShippingData() && !shipment.hasTrackingSubscription())
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        ShippingProvider provider = trackingProvider(storeId);
        if (provider == null) {
            return;
        }
        for (Shipment shipment : candidates) {
            subscribeOne(storeId, orderId, rmaId, shipment, provider);
        }
    }

    private void subscribeOne(String storeId, String orderId, String rmaId, Shipment shipment, ShippingProvider provider) {
        LocalDateTime now = LocalDateTime.now();
        Optional<ShipmentTracking> existing = shipmentTrackingsRepository.find(storeId, shipment.getTrackingNo());
        if (existing.isPresent()) {
            ShipmentTracking tracking = existing.get();
            boolean sameEntity = (orderId != null && orderId.equals(tracking.getOrderId()))
                    || (rmaId != null && rmaId.equals(tracking.getRmaId()));
            if (!sameEntity) {
                fail(orderId, shipment, DUPLICATE_TRACKING_NO, now);
                return;
            }
        } else {
            boolean indexed = shipmentTrackingsRepository.saveIfAbsent(
                    new ShipmentTracking(storeId, shipment.getTrackingNo(), orderId, rmaId, now));
            if (!indexed) {
                fail(orderId, shipment, DUPLICATE_TRACKING_NO, now);
                return;
            }
        }
        if (shipment.getExternalId() != null) {
            shipment.markTrackingActive(shipment.getExternalId(), now);
            return;
        }
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
                log.warn("Tracking subscription failed store={} order={} trackingNo={}: {}",
                        storeId, orderId, shipment.getTrackingNo(), e.getMessage());
                fail(orderId, shipment, e.getMessage(), now);
                return;
            }
        }
        if (result.status() == ParcelTrackingSubscription.Status.PENDING && orderId == null) {
            // RMA shipments come from the shipping provider and are ACTIVE right away; there is no
            // re-check queue for RMA, so a PENDING result must not be left waiting forever
            fail(null, shipment, RMA_RETRY_UNSUPPORTED, now);
            return;
        }
        apply(shipment, result, now);
        if (result.status() == ParcelTrackingSubscription.Status.PENDING) {
            publisher.publish(new ShipmentTrackingCheckRequest(storeId, orderId, shipment.getTrackingNo()));
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
        persist(request, result, now);
        if (result.status() == ParcelTrackingSubscription.Status.FAILED) {
            orderEventsRepository.save(new OrderEvent(request.getOrderId(), EventType.action, TRACKING_FAILED_EVENT, now));
        }
    }

    // the order may have been edited while the re-check was queued: apply the outcome to the freshly
    // loaded entity and let the executor retry on a version conflict instead of burning a delivery attempt
    private void persist(ShipmentTrackingCheckRequest request, ParcelTrackingSubscription result, LocalDateTime now) {
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(request.getStoreId(), request.getOrderId()),
                fresh -> {
                    Shipment target = pendingShipment(fresh, request.getTrackingNo());
                    if (target != null) {
                        apply(target, result, now);
                    }
                },
                ordersRepository::save);
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
        ShippingProvider provider = shippingProviderFactory.get(store);
        return provider != null && provider.supportsParcelTracking() ? provider : null;
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

    private void apply(Shipment shipment, ParcelTrackingSubscription result, LocalDateTime now) {
        switch (result.status()) {
            case ACTIVE -> shipment.markTrackingActive(result.externalId(), now);
            case PENDING -> shipment.markTrackingPending(result.subscriptionId(), now);
            case FAILED -> shipment.markTrackingFailed(result.error(), now);
        }
    }

    private void fail(String orderId, Shipment shipment, String error, LocalDateTime now) {
        shipment.markTrackingFailed(error, now);
        if (orderId != null) {
            orderEventsRepository.save(new OrderEvent(orderId, EventType.action, TRACKING_FAILED_EVENT, now));
        }
    }

    private static ParcelTrackingRequest trackingRequest(Shipment shipment, String orderId, String rmaId) {
        return new ParcelTrackingRequest(shipment.getTrackingNo(), shipment.getCarrier(), label(orderId, rmaId));
    }

    private static String label(String orderId, String rmaId) {
        return orderId != null ? "CommerceLink order " + orderId : "CommerceLink RMA " + rmaId;
    }
}
