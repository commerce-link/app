package pl.commercelink.shipping.tracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.shipping.ShipmentTrackingsRepository;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.TrackingEvent;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Asks the parcel's integration for its tracking history and hands the furthest state to the common status path.
 * Never throws for an integration error: the next hourly sweep asks again, so a retry through the queue would only
 * add load (and a dead-letter entry per parcel while Allegro is down).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentTrackingPoller {

    private final StoresRepository storesRepository;
    private final ShippingProviders shippingProviders;
    private final ShipmentTrackingsRepository shipmentTrackingsRepository;
    private final ShipmentTrackingUpdates shipmentTrackingUpdates;

    public void poll(ShipmentTrackingPollRequest request) {
        Store store = storesRepository.findById(request.getStoreId());
        if (store == null) {
            return;
        }
        Optional<ShipmentTracking> row = shipmentTrackingsRepository.find(request.getStoreId(), request.getTrackingNo());
        if (row.isEmpty() || row.get().getExternalId() == null
                || (row.get().currentState() != null && row.get().currentState().isFinal())) {
            return;
        }
        Optional<ShippingProvider> provider = shippingProviders.forName(store, request.getProvider());
        if (provider.isEmpty()) {
            log.warn("Tracking poll skipped: integration {} is no longer connected in store={} (trackingNo={})",
                    request.getProvider(), request.getStoreId(), request.getTrackingNo());
            return;
        }
        List<TrackingEvent> events;
        try {
            events = provider.get().getTrackingEvents(row.get().getExternalId());
        } catch (RuntimeException e) {
            log.warn("Tracking poll failed store={} trackingNo={} provider={}: {}",
                    request.getStoreId(), request.getTrackingNo(), request.getProvider(), e.getMessage());
            return;
        }
        Optional<Reached> reached = furthest(events);
        if (reached.isPresent()) {
            try {
                shipmentTrackingUpdates.apply(request.getStoreId(), request.getTrackingNo(),
                        reached.get().state(), reached.get().at());
            } catch (RuntimeException e) {
                // apply has already reverted the state; not marking the poll lets the next sweep retry at once
                log.warn("Tracking status {} could not be applied store={} trackingNo={}: {}",
                        reached.get().state(), request.getStoreId(), request.getTrackingNo(), e.getMessage());
                return;
            }
        }
        // read again: apply may have moved the state, and the poll time must not write an older state back
        shipmentTrackingsRepository.find(request.getStoreId(), request.getTrackingNo())
                .ifPresent(fresh -> shipmentTrackingsRepository.markPolled(fresh, LocalDateTime.now()));
    }

    static Optional<Reached> furthest(List<TrackingEvent> events) {
        return events.stream()
                .map(ShipmentTrackingPoller::reached)
                .flatMap(Optional::stream)
                .max(Comparator.comparing((Reached r) -> r.state() == ShipmentTrackingState.DELIVERED)
                        .thenComparing(Reached::at));
    }

    private static Optional<Reached> reached(TrackingEvent event) {
        ShipmentTrackingState state;
        if ("DELIVERED".equals(event.state())) {
            state = ShipmentTrackingState.DELIVERED;
        } else if ("COLLECTED".equals(event.state())) {
            state = ShipmentTrackingState.COLLECTED;
        } else {
            return Optional.empty();
        }
        LocalDateTime at = event.datetime() == null
                ? LocalDateTime.now()
                : event.datetime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return Optional.of(new Reached(state, at));
    }

    record Reached(ShipmentTrackingState state, LocalDateTime at) {
    }
}
