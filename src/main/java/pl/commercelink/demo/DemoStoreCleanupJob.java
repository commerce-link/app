package pl.commercelink.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = {"app.registration.enabled", "app.registration.demo"}, havingValue = "true")
@RequiredArgsConstructor
public class DemoStoreCleanupJob {

    private final StoresRepository storesRepository;
    private final StoreDeletionService storeDeletionService;

    @Scheduled(cron = "0 15 * * * ?")
    public void deleteExpiredDemoStores() {
        deleteExpiredDemoStores(Instant.now());
    }

    void deleteExpiredDemoStores(Instant now) {
        for (Store store : storesRepository.findAll()) {
            try {
                if (store.isDemoExpired(now)) {
                    storeDeletionService.deleteDemoStore(store.getStoreId());
                }
            } catch (RuntimeException e) {
                System.err.println("[DemoStoreCleanup] Failed to delete expired store " + store.getStoreId() + ": " + e.getMessage());
            }
        }
    }
}
