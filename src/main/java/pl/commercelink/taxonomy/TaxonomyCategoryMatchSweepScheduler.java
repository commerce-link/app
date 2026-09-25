package pl.commercelink.taxonomy;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local trigger for the category match sweep. On AWS the sweep is driven by an EventBridge schedule so that
 * only one instance sweeps per tick; outside AWS there is no scheduler, so a plain cron takes over.
 * application.env is only set to "localhost" by the localdev profile and to "prod" by the deployed
 * environments, so a missing value has to count as local.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
@RequiredArgsConstructor
class TaxonomyCategoryMatchSweepScheduler {

    private final TaxonomyCategoryMatchSweep sweep;

    @Scheduled(cron = "${taxonomy.category-match.sweep-cron:0 2-57/5 * * * ?}")
    void trigger() {
        sweep.sweep();
    }
}
