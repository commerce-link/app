package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private static ReceiptAttempt deadAttempt(String receiptKey, ReceiptAttemptState state, int attemptNo) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey(receiptKey);
        attempt.setState(state);
        attempt.setAttemptNo(attemptNo);
        return attempt;
    }

    @Test
    void canResendEmailIsTrueForAGenuinelyFailedUnleasedAttempt() {
        ReceiptAttempt attempt = failedEmailAttempt();

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.canResendEmail()).isTrue());
    }

    @Test
    void canResendEmailIsFalseWhileTheProcessorHoldsTheLeaseEvenThoughTheAttemptLooksFailed() {
        // Same race the service guards against: mid-send the attempt already satisfies emailFailed(), so the row
        // must not offer "resend" until the lease clears.
        ReceiptAttempt attempt = failedEmailAttempt();
        attempt.setLeaseUntil(NOW.plus(Duration.ofMinutes(10)));

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.canResendEmail()).isFalse());
    }

    @Test
    void problemIsResolvedInTheViewersLocale() {
        ReceiptAttempt attempt = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        when(alerts.message(eq(attempt), eq(ReceiptAttention.FAILED), eq(Locale.ENGLISH))).thenReturn("Fiscalisation failed.");

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement()
                .satisfies(row -> assertThat(row.problem()).isEqualTo("Fiscalisation failed."));
    }

    @Test
    void onlyAttemptThatIsDeadStillShowsItsProblemWhenNoNewerAttemptExists() {
        ReceiptAttempt attempt = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        when(alerts.message(eq(attempt), eq(ReceiptAttention.FAILED), eq(Locale.ENGLISH))).thenReturn("Fiscalisation failed.");

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.problem()).isNotNull());
    }

    @Test
    void aSupersededDeadAttemptsProblemIsHiddenWhileTheNewestAttemptHasNone() {
        // R1 FAILED but a newer R2 already fiscalised: R1's old FAILED reason no longer matters, and R2 (the
        // attempt that still matters) has nothing wrong with it either.
        ReceiptAttempt r1 = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        ReceiptAttempt r2 = deadAttempt("order-1:R2", ReceiptAttemptState.FISCALISED, 2);

        ReceiptOrderView view = ReceiptOrderView.of(List.of(r1, r2), false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).extracting(row -> row.problem()).containsOnlyNulls();
        // the superseded row's problem is suppressed before ever asking for its message
        verifyNoInteractions(alerts);
    }
}
