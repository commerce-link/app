package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoStoreCleanupListenerTest {

    @Mock private DemoStoreCleanup cleanup;

    @InjectMocks private DemoStoreCleanupListener listener;

    @Test
    void aTriggerMessageRunsExactlyOneCleanup() {
        // when
        listener.handleMessage("scheduled trigger");

        // then
        verify(cleanup).deleteExpiredDemoStores();
    }
}
