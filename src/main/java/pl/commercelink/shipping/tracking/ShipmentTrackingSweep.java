package pl.commercelink.shipping.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.shipping.ShipmentTrackingsRepository;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hourly pass over the parcels of shipping integrations without webhooks (Wysyłam z Allegro): every parcel that is
 * due gets one poll message; a parcel without delivery after 30 days stops being polled and is reported for a manual
 * check. Parcels of integrations with webhooks, and numbers typed in by hand, are never polled.
 */
@Slf4j
@Component
public class ShipmentTrackingSweep {

    static final Duration POLL_INTERVAL = Duration.ofMinutes(50);
    static final Duration TRACKING_HORIZON = Duration.ofDays(30);

    private final StoresRepository storesRepository;
    private final ShippingProviders shippingProviders;
    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final ShipmentTrackingUpdates shipmentTrackingUpdates;
    private final ShipmentTrackingPollPublisher publisher;
    private final Clock clock;

    @Autowired
    public ShipmentTrackingSweep(StoresRepository storesRepository, ShippingProviders shippingProviders,
                                 ShipmentTrackingsRepository shipmentTrackingsRepository,
                                 ShipmentTrackingUpdates shipmentTrackingUpdates, ShipmentTrackingPollPublisher publisher) {
        this(storesRepository, shippingProviders, shipmentTrackingsRepository, shipmentTrackingUpdates, publisher,
                Clock.systemDefaultZone());
    }

    ShipmentTrackingSweep(StoresRepository storesRepository, ShippingProviders shippingProviders,
                          ShipmentTrackingsRepository shipmentTrackingsRepository,
                          ShipmentTrackingUpdates shipmentTrackingUpdates, ShipmentTrackingPollPublisher publisher,
                          Clock clock) {
        this.storesRepository = storesRepository;
        this.shippingProviders = shippingProviders;
        this.shipmentTrackingsRepository = shipmentTrackingsRepository;
        this.shipmentTrackingUpdates = shipmentTrackingUpdates;
        this.publisher = publisher;
        this.clock = clock;
    }

    public void sweep() {
        LocalDateTime now = LocalDateTime.now(clock);
        int published = 0;
        int expired = 0;
        for (Store store : storesRepository.findAll()) {
            try {
                Set<String> polled = pollingIntegrations(store);
                if (polled.isEmpty()) {
                    continue;
                }
                for (ShipmentTracking row : shipmentTrackingsRepository.findByStore(store.getStoreId())) {
                    if (!isCandidate(row, polled)) {
                        continue;
                    }
                    if (isExpired(row, now)) {
                        if (expire(store, row, now)) {
                            expired++;
                        }
                    } else if (isDue(row, now)) {
                        publisher.publish(new ShipmentTrackingPollRequest(
                                store.getStoreId(), row.getTrackingNo(), row.getProvider()));
                        published++;
                    }
                }
            } catch (RuntimeException e) {
                log.error("Shipment tracking sweep failed for store {}", store.getStoreId(), e);
            }
        }
        if (published + expired > 0) {
            log.info("Shipment tracking sweep published {} polls, expired {} parcels", published, expired);
        }
    }

    private boolean expire(Store store, ShipmentTracking row, LocalDateTime now) {
        try {
            return shipmentTrackingUpdates.apply(store.getStoreId(), row.getTrackingNo(), ShipmentTrackingState.EXPIRED, now);
        } catch (RuntimeException e) {
            log.warn("Shipment expiry failed store={} trackingNo={}: {}", store.getStoreId(), row.getTrackingNo(), e.getMessage());
            return false;
        }
    }

    private Set<String> pollingIntegrations(Store store) {
        return store.shippingIntegrationNames().stream()
                .filter(name -> shippingProviders.forName(store, name)
                        .map(ShippingProvider::supportsTrackingPolling)
                        .orElse(false))
                .collect(Collectors.toSet());
    }

    static boolean isCandidate(ShipmentTracking row, Set<String> polled) {
        ShipmentTrackingState state = row.currentState();
        return row.getProvider() != null && polled.contains(row.getProvider())
                && row.getExternalId() != null
                && (state == null || !state.isFinal());
    }

    static boolean isExpired(ShipmentTracking row, LocalDateTime now) {
        return row.getCreatedAt() != null && row.getCreatedAt().plus(TRACKING_HORIZON).isBefore(now);
    }

    static boolean isDue(ShipmentTracking row, LocalDateTime now) {
        return row.getLastPolledAt() == null || row.getLastPolledAt().plus(POLL_INTERVAL).isBefore(now);
    }
}
