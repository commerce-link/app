package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
public class DropshipTrackingSweep {

    private final StoresRepository storesRepository;
    private final DeliveriesRepository deliveriesRepository;
    private final DropshipTrackingEventPublisher publisher;
    private final DropshipTrackingProperties properties;
    private final Clock clock;

    @Autowired
    public DropshipTrackingSweep(StoresRepository storesRepository, DeliveriesRepository deliveriesRepository,
                                 DropshipTrackingEventPublisher publisher, DropshipTrackingProperties properties) {
        this(storesRepository, deliveriesRepository, publisher, properties, Clock.systemDefaultZone());
    }

    DropshipTrackingSweep(StoresRepository storesRepository, DeliveriesRepository deliveriesRepository,
                          DropshipTrackingEventPublisher publisher, DropshipTrackingProperties properties, Clock clock) {
        this.storesRepository = storesRepository;
        this.deliveriesRepository = deliveriesRepository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${dropship.tracking.sweep-cron:0 7-52/15 * * * ?}")
    public void sweep() {
        LocalDateTime now = LocalDateTime.now(clock);
        int published = 0;
        for (Store store : storesRepository.findAll()) {
            try {
                for (Delivery delivery : deliveriesRepository.findTrackableDropshipDeliveries(store.getStoreId())) {
                    if (isDue(delivery, now)) {
                        publisher.publish(new DropshipTrackingEventRequest(
                                store.getStoreId(), delivery.getDeliveryId(), delivery.getExternalDeliveryId()));
                        published++;
                    }
                }
            } catch (RuntimeException e) {
                log.error("Dropship tracking sweep failed for store {}", store.getStoreId(), e);
            }
        }
        if (published > 0) {
            log.info("Dropship tracking sweep published {} supplier checks", published);
        }
    }

    boolean isDue(Delivery delivery, LocalDateTime now) {
        if (delivery.getOrderedAt() == null || delivery.getOrderedAt().plus(properties.initialDelay()).isAfter(now)) {
            return false;
        }
        return delivery.getTrackingView().isDue(now);
    }
}
