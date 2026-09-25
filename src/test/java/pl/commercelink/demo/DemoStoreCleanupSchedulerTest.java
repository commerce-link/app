package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoStoreCleanupSchedulerTest {

    @Mock private DemoStoreCleanup cleanup;

    @InjectMocks private DemoStoreCleanupScheduler scheduler;

    @Test
    void theLocalCronRunsExactlyOneCleanup() {
        // when
        scheduler.trigger();

        // then
        verify(cleanup).deleteExpiredDemoStores();
    }
}
