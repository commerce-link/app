package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReceiptSweepTest {

    private final InMemoryReceiptAttemptStore attempts = new InMemoryReceiptAttemptStore();
    private final ReceiptWorkPublisher publisher = mock(ReceiptWorkPublisher.class);
    private final MutableClock clock = MutableClock.at("2026-09-23T13:00:00Z");
    private final ReceiptSweep sweep = new ReceiptSweep(attempts, publisher, clock);

    private void attempt(String key, Duration dueIn) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("s1");
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
}
