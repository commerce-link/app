package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryMatchSweepSchedulerTest {

    @Mock private TaxonomyCategoryMatchSweep sweep;

    @InjectMocks private TaxonomyCategoryMatchSweepScheduler scheduler;

    @Test
    void theLocalCronRunsExactlyOneSweep() {
        // when
        scheduler.trigger();

        // then
        verify(sweep).sweep();
    }
}
