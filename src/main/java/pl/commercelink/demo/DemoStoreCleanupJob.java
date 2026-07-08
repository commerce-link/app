package pl.commercelink.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.demo.registration.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoStoreCleanupJob {

    private final StoresRepository storesRepository;
    private final DemoStoreDeletionService demoStoreDeletionService;

    @Scheduled(cron = "0 15 * * * ?")
    public void deleteExpiredDemoStores() {
        deleteExpiredDemoStores(Instant.now());
    }

    void deleteExpiredDemoStores(Instant now) {
        for (Store store : storesRepository.findAll()) {
            if (!store.isDemoExpired(now)) {
                continue;
            }
            try {
                demoStoreDeletionService.deleteDemoStore(store.getStoreId());
            } catch (RuntimeException e) {
                System.err.println("[DemoStoreCleanup] Failed to delete expired store " + store.getStoreId() + ": " + e.getMessage());
            }
        }
    }
}
