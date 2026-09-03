package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local trigger for the dropship tracking sweep. On AWS the sweep is driven by an EventBridge schedule so that
 * only one instance polls the suppliers; outside AWS there is no scheduler, so a plain cron takes over.
 * application.env is only set to "localhost" by the localdev profile and to "prod" by the deployed
 * environments, so a missing value has to count as local.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
@RequiredArgsConstructor
class DropshipTrackingSweepScheduler {

    private final DropshipTrackingSweep sweep;

    @Scheduled(cron = "${dropship.tracking.sweep-cron:0 7 * * * ?}")
    void trigger() {
        sweep.sweep();
    }
}
