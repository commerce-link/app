package pl.commercelink.stores.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local trigger for the store lifecycle sweep. On AWS the sweep is driven by an EventBridge schedule so that only one
 * instance runs it; outside AWS there is no scheduler, so a plain cron takes over. application.env is only set to
 * "localhost" by the localdev profile and to "prod" by the deployed environments, so a missing value counts as local.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
@RequiredArgsConstructor
class StoreLifecycleSweepScheduler {

    private final StoreLifecycleSweep sweep;

    @Scheduled(cron = "${store.lifecycle.sweep-cron:0 17 * * * ?}")
    void trigger() {
        sweep.sweep();
    }
}
