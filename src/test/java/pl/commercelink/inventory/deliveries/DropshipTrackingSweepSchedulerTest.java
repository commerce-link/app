package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropshipTrackingSweepSchedulerTest {

    @Mock private DropshipTrackingSweep sweep;

    @InjectMocks private DropshipTrackingSweepScheduler scheduler;

    @Test
    void theLocalCronRunsExactlyOneSweep() {
        scheduler.trigger();

        verify(sweep).sweep();
    }
}
