package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoStoreCleanupJobTest {

    @Mock private StoresRepository storesRepository;
    @Mock private StoreDeletionService storeDeletionService;
    @InjectMocks private DemoStoreCleanupJob job;

    private Store store(String storeId, DemoStoreMetadata demo) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setDemo(demo);
        return store;
    }

    @Test
    void deletesOnlyExpiredDemoStores() {
        // given
        Instant now = Instant.parse("2026-07-08T12:00:00Z");
        Store regular = store("regular0001", null);
        Store active = store("active00001", new DemoStoreMetadata("a@example.com", "2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z"));
        Store expired = store("expired0001", new DemoStoreMetadata("b@example.com", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z"));
        when(storesRepository.findAll()).thenReturn(List.of(regular, active, expired));

        // when
        job.deleteExpiredDemoStores(now);

        // then
        verify(storeDeletionService).deleteDemoStore("expired0001");
        verify(storeDeletionService, never()).deleteDemoStore("regular0001");
        verify(storeDeletionService, never()).deleteDemoStore("active00001");
    }

    @Test
    void continuesSweepWhenExpiryTimestampIsMalformed() {
        // given
        Instant now = Instant.parse("2026-07-08T12:00:00Z");
        Store corrupted = store("corrupt0001", new DemoStoreMetadata("a@example.com", "2026-06-01T00:00:00Z", "not-a-timestamp"));
        Store expired = store("expired0001", new DemoStoreMetadata("b@example.com", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z"));
        when(storesRepository.findAll()).thenReturn(List.of(corrupted, expired));

        // when
        job.deleteExpiredDemoStores(now);

        // then
        verify(storeDeletionService).deleteDemoStore("expired0001");
        verify(storeDeletionService, never()).deleteDemoStore("corrupt0001");
    }

    @Test
    void continuesSweepWhenSingleDeletionFails() {
        // given
        Instant now = Instant.parse("2026-07-08T12:00:00Z");
        Store first = store("expired0001", new DemoStoreMetadata("a@example.com", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z"));
        Store second = store("expired0002", new DemoStoreMetadata("b@example.com", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z"));
        when(storesRepository.findAll()).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(storeDeletionService).deleteDemoStore("expired0001");

        // when
        job.deleteExpiredDemoStores(now);

        // then
        verify(storeDeletionService).deleteDemoStore("expired0002");
    }
}
