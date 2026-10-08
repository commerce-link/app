package pl.commercelink.shipping.tracking;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local trigger for the shipment tracking sweep. On AWS an EventBridge schedule drives it so that one instance
 * polls; outside AWS there is no scheduler, so a plain cron takes over (same rule as the dropship sweep).
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
@RequiredArgsConstructor
class ShipmentTrackingSweepScheduler {

    private final ShipmentTrackingSweep sweep;

    @Scheduled(cron = "${shipment.tracking.sweep-cron:0 5 * * * ?}")
    void trigger() {
        sweep.sweep();
    }
}
