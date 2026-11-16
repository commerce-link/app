package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreInventoryRefresherTest {

    @Mock
    private ExecutorService executor;
    @Mock
    private Runnable refresh;
    @Captor
    private ArgumentCaptor<Runnable> submitted;

    @InjectMocks
    private StoreInventoryRefresher refresher;

    @Test
    void startsOneRefreshPerStoreWhileItRuns() {
        // when
        refresher.refreshInBackground("store-1", refresh);
        refresher.refreshInBackground("store-1", refresh);

        // then
        verify(executor, times(1)).execute(any());
    }

    @Test
    void storesAreRefreshedIndependently() {
        // when
        refresher.refreshInBackground("store-1", refresh);
        refresher.refreshInBackground("store-2", refresh);

        // then
        verify(executor, times(2)).execute(any());
    }

    @Test
    void finishedRefreshAllowsTheNextOne() {
        // given
        refresher.refreshInBackground("store-1", refresh);
        verify(executor).execute(submitted.capture());
        submitted.getValue().run();

        // when
        refresher.refreshInBackground("store-1", refresh);

        // then
        verify(refresh).run();
        verify(executor, times(2)).execute(any());
    }

    @Test
    void failedRefreshAllowsTheNextOne() {
        // given
        doThrow(new IllegalStateException("rates unavailable")).when(refresh).run();
        refresher.refreshInBackground("store-1", refresh);
        verify(executor).execute(submitted.capture());
        submitted.getValue().run();

        // when
        refresher.refreshInBackground("store-1", refresh);

        // then
        verify(executor, times(2)).execute(any());
    }

    @Test
    void rejectedRefreshAllowsTheNextOne() {
        // given
        doThrow(new RejectedExecutionException("shut down")).when(executor).execute(any());
        refresher.refreshInBackground("store-1", refresh);

        // when
        refresher.refreshInBackground("store-1", refresh);

        // then
        verify(executor, times(2)).execute(any());
    }
}
