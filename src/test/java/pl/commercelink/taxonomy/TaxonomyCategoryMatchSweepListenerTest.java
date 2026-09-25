package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryMatchSweepListenerTest {

    @Mock private TaxonomyCategoryMatchSweep sweep;

    @InjectMocks private TaxonomyCategoryMatchSweepListener listener;

    @Test
    void aTriggerMessageRunsExactlyOneSweep() {
        // when
        listener.handleMessage("scheduled trigger");

        // then
        verify(sweep).sweep();
    }
}
