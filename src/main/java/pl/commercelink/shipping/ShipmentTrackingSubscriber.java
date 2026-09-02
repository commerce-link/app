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
import pl.commercelink.shipping.api.ParcelTrackingRequest;
import pl.commercelink.shipping.api.ParcelTrackingSubscription;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
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

    private final StoresRepository storesRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final ShipmentTrackingEventPublisher publisher;
    private final OrderEventsRepository orderEventsRepository;
    private final OrdersRepository ordersRepository;

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
        try {
            ParcelTrackingSubscription result = provider.trackParcel(
                    new ParcelTrackingRequest(shipment.getTrackingNo(), shipment.getCarrier(), label(orderId, rmaId)));
            apply(shipment, result, now);
            if (result.status() == ParcelTrackingSubscription.Status.PENDING && orderId != null) {
                publisher.publish(new ShipmentTrackingCheckRequest(storeId, orderId, shipment.getTrackingNo()));
            }
        } catch (RuntimeException e) {
            log.warn("Tracking subscription failed store={} order={} trackingNo={}: {}",
                    storeId, orderId, shipment.getTrackingNo(), e.getMessage());
            fail(orderId, shipment, e.getMessage(), now);
        }
    }

    public void check(ShipmentTrackingCheckRequest request, int attempt) {
        Order order = ordersRepository.findById(request.getStoreId(), request.getOrderId());
        if (order == null) {
            return;
        }
        Shipment shipment = order.getShipments().stream()
                .filter(s -> s.hasTrackingNo(request.getTrackingNo()) && s.isTrackingPending())
                .findFirst()
                .orElse(null);
        if (shipment == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ShippingProvider provider = trackingProvider(request.getStoreId());
        if (provider == null) {
            fail(order.getOrderId(), shipment, PROVIDER_UNAVAILABLE, now);
            ordersRepository.save(order);
            return;
        }
        ParcelTrackingSubscription result = provider.checkParcelTracking(shipment.getTrackingSubscriptionId());
        if (result.status() == ParcelTrackingSubscription.Status.PENDING) {
            if (attempt < MAX_CHECK_ATTEMPTS) {
                throw new ShipmentTrackingPendingException(request.getTrackingNo(), attempt);
            }
            fail(order.getOrderId(), shipment, CHECK_TIMED_OUT, now);
            ordersRepository.save(order);
            return;
        }
        apply(shipment, result, now);
        ordersRepository.save(order);
    }

    private ShippingProvider trackingProvider(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return null;
        }
        ShippingProvider provider = shippingProviderFactory.get(store);
        return provider != null && provider.supportsParcelTracking() ? provider : null;
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

    private static String label(String orderId, String rmaId) {
        return orderId != null ? "CommerceLink order " + orderId : "CommerceLink RMA " + rmaId;
    }
}
