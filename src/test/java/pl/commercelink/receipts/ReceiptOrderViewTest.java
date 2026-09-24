package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReceiptOrderViewTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private final ReceiptAlerts alerts = mock(ReceiptAlerts.class);

    private static ReceiptAttempt failedEmailAttempt() {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey("order-1:R1");
        attempt.setState(ReceiptAttemptState.FISCALISED);
        attempt.setDocumentUrl("https://paragony.pl/x");
        attempt.setEmailClaimedAt(NOW.minus(Duration.ofMinutes(1)));
        return attempt;
    }

    @Test
    void canResendEmailIsTrueForAGenuinelyFailedUnleasedAttempt() {
        ReceiptAttempt attempt = failedEmailAttempt();

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, alerts, NOW);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.canResendEmail()).isTrue());
    }

    @Test
    void canResendEmailIsFalseWhileTheProcessorHoldsTheLeaseEvenThoughTheAttemptLooksFailed() {
        // Same race the service guards against: mid-send the attempt already satisfies emailFailed(), so the row
        // must not offer "resend" until the lease clears.
        ReceiptAttempt attempt = failedEmailAttempt();
        attempt.setLeaseUntil(NOW.plus(Duration.ofMinutes(10)));

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, alerts, NOW);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.canResendEmail()).isFalse());
    }
}
