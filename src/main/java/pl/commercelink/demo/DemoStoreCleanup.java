package pl.commercelink.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.registration.demo", havingValue = "true")
@RequiredArgsConstructor
public class DemoStoreCleanup {

    private final StoresRepository storesRepository;
    private final StoreDeletionService storeDeletionService;

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
                log.error("Failed to delete expired demo store {}", store.getStoreId(), e);
            }
        }
    }
}
