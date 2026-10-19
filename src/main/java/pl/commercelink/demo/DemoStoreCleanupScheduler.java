package pl.commercelink.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local trigger for the expired demo store cleanup. On AWS the cleanup is driven by an EventBridge schedule so
 * that only one instance deletes the expired stores; outside AWS there is no scheduler, so a plain cron takes over.
 * application.env is only set to "localhost" by the localdev profile and to "prod" by the deployed
 * environments, so a missing value has to count as local.
 */
@Component
@ConditionalOnProperty(name = "app.registration.demo", havingValue = "true")
@ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
@RequiredArgsConstructor
class DemoStoreCleanupScheduler {

    private final DemoStoreCleanup cleanup;

    @Scheduled(cron = "0 15 * * * ?")
    void trigger() {
        cleanup.deleteExpiredDemoStores();
    }
}
