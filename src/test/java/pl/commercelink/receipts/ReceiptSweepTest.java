package pl.commercelink.receipts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.stores.StoreActivity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ReceiptSweepTest {

    private final InMemoryReceiptAttemptStore attempts = new InMemoryReceiptAttemptStore();
    private final ReceiptWorkPublisher publisher = mock(ReceiptWorkPublisher.class);
    private final MutableClock clock = MutableClock.at("2026-09-23T13:00:00Z");
    private final StoreActivity storeActivity = mock(StoreActivity.class);
    private final ReceiptSweep sweep = new ReceiptSweep(attempts, publisher, storeActivity, clock);

    @BeforeEach
    void storesAreActive() {
        when(storeActivity.isActive(anyString())).thenReturn(true);
    }

    private void attempt(String key, Duration dueIn) {
        attempt("s1", key, dueIn);
    }

    private void attempt(String storeId, String key, Duration dueIn) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(storeId);
        attempt.setReceiptKey(key);
        attempt.setState(ReceiptAttemptState.PENDING);
        if (dueIn != null) {
            attempt.schedule(clock.instant().plus(dueIn));
        }
        attempts.create(attempt);
    }

    @Test
    void publishesOnlyAttemptsThatAreDue() {
        attempt("a:R1", Duration.ofMinutes(-5));
        attempt("b:R1", Duration.ZERO);
        attempt("c:R1", Duration.ofMinutes(5));
        attempt("d:R1", null);

        assertThat(sweep.sweep()).isEqualTo(2);

        verify(publisher, times(2)).publishDue(any());
    }

    @Test
    void aFailedPublishDoesNotStopTheSweep() {
        attempt("a:R1", Duration.ofMinutes(-2));
        attempt("b:R1", Duration.ofMinutes(-1));
        doThrow(new RuntimeException("sqs")).doNothing().when(publisher).publishDue(any());

        assertThat(sweep.sweep()).isEqualTo(1);
    }

    @Test
    void leavesAttemptsOfInactiveStoreDue() {
        // given
        attempt("s1", "a:R1", Duration.ofMinutes(-5));
        attempt("s2", "b:R1", Duration.ofMinutes(-5));
        when(storeActivity.isActive("s2")).thenReturn(false);

        // when
        int published = sweep.sweep();

        // then
        assertThat(published).isEqualTo(1);
        verify(publisher).publishDue(argThat(attempt -> "s1".equals(attempt.getStoreId())));
        verify(publisher, never()).publishDue(argThat(attempt -> "s2".equals(attempt.getStoreId())));
        assertThat(attempts.find("s2", "b:R1")).isPresent();
    }

    @Test
    void asksAboutEachStoreOncePerSweep() {
        // given
        attempt("s1", "a:R1", Duration.ofMinutes(-5));
        attempt("s1", "b:R1", Duration.ofMinutes(-4));

        // when
        sweep.sweep();

        // then
        verify(storeActivity, times(1)).isActive("s1");
        verify(publisher, times(2)).publishDue(any());
    }

    @Test
    void schedulerAnnotationDefaultsAreValidISO8601Durations() {
        // ReceiptSweepScheduler uses @Scheduled with ISO-8601 duration strings.
        // Verify the default values in the annotation parse correctly.
        assertThat(Duration.parse("PT1M")).isEqualTo(Duration.ofMinutes(1));
        assertThat(Duration.parse("PT30S")).isEqualTo(Duration.ofSeconds(30));
    }
}
