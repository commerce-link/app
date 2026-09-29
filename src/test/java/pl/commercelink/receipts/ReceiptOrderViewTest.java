package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.canResendEmail()).isTrue());
    }

    @Test
    void canResendEmailIsFalseWhileTheProcessorHoldsTheLeaseEvenThoughTheAttemptLooksFailed() {
        // Same race the service guards against: mid-send the attempt already satisfies emailFailed(), so the row
        // must not offer "resend" until the lease clears.
        ReceiptAttempt attempt = failedEmailAttempt();
        attempt.setLeaseUntil(NOW.plus(Duration.ofMinutes(10)));

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.canResendEmail()).isFalse());
    }

    @Test
    void problemIsTheOrderPageWordingInTheViewersLocaleNotTheBellMessage() {
        // given
        ReceiptAttempt attempt = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        ReceiptPageProblem problem = new ReceiptPageProblem("The Dev system rejected the receipt: VAT.",
                "Fix the cause and click \"Reissue\".", null, null);
        when(alerts.pageProblem(eq(attempt), eq(ReceiptAttention.FAILED), eq(Locale.ENGLISH))).thenReturn(problem);

        // when
        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, false, alerts, NOW, Locale.ENGLISH);

        // then: the bell's message is never asked for on the order page
        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.problem()).isSameAs(problem));
        verify(alerts, never()).message(any(), any(), any());
    }

    @Test
    void onlyAttemptThatIsDeadStillShowsItsProblemWhenNoNewerAttemptExists() {
        ReceiptAttempt attempt = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        when(alerts.pageProblem(eq(attempt), eq(ReceiptAttention.FAILED), eq(Locale.ENGLISH)))
                .thenReturn(new ReceiptPageProblem("Rejected.", null, null, null));

        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).singleElement().satisfies(row -> assertThat(row.problem()).isNotNull());
    }

    @Test
    void aSupersededDeadAttemptsProblemIsHiddenWhileTheNewestAttemptHasNone() {
        // R1 FAILED but a newer R2 already fiscalised: R1's old FAILED reason no longer matters, and R2 (the
        // attempt that still matters) has nothing wrong with it either.
        ReceiptAttempt r1 = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        ReceiptAttempt r2 = deadAttempt("order-1:R2", ReceiptAttemptState.FISCALISED, 2);

        ReceiptOrderView view = ReceiptOrderView.of(List.of(r1, r2), false, false, alerts, NOW, Locale.ENGLISH);

        assertThat(view.rows()).extracting(row -> row.problem()).containsOnlyNulls();
        // the superseded row's problem is suppressed before ever asking for its message (only its short outcome is
        // asked for, which carries no advice)
        verify(alerts, never()).pageProblem(any(), any(), any());
        verify(alerts, never()).message(any(), any(), any());
    }

    @Test
    void aSupersededDeadAttemptKeepsItsShortOutcomeAndItsOwnNumbers() {
        // given: R1 blocked, superseded by R2 fiscalised with its number
        ReceiptAttempt r1 = deadAttempt("order-1:R1", ReceiptAttemptState.BLOCKED, 1);
        ReceiptAttempt r2 = deadAttempt("order-1:R2", ReceiptAttemptState.FISCALISED, 2);
        r2.setReceiptNumber("PAR/2/2026");
        r2.setFiscalisedAt(NOW);
        when(alerts.outcome(r1, Locale.ENGLISH)).thenReturn("no lines above 0 PLN");

        // when
        ReceiptOrderView view = ReceiptOrderView.of(List.of(r1, r2), false, false, alerts, NOW, Locale.ENGLISH);

        // then: newest first; the outcome is asked for dead attempts only
        assertThat(view.rows()).extracting(ReceiptOrderView.Row::attemptNo).containsExactly(2, 1);
        assertThat(view.rows().get(0).receiptNumber()).isEqualTo("PAR/2/2026");
        assertThat(view.rows().get(0).fiscalisedAt()).isEqualTo(NOW);
        assertThat(view.rows().get(0).outcome()).isNull();
        assertThat(view.rows().get(1).outcome()).isEqualTo("no lines above 0 PLN");
        verify(alerts, never()).outcome(eq(r2), any());
    }

    @Test
    void aDeadAttemptOfAnOrderThatGotItsSaleDocumentAnotherWayNeedsNothing() {
        // given: the newest attempt blocked, then a receipt from the shop's cash register was typed in
        ReceiptAttempt blocked = deadAttempt("order-1:R1", ReceiptAttemptState.BLOCKED, 1);
        when(alerts.outcome(blocked, Locale.ENGLISH)).thenReturn("a point-of-sale sale without the customer's email");

        // when
        ReceiptOrderView view = ReceiptOrderView.of(List.of(blocked), false, true, alerts, NOW, Locale.ENGLISH);

        // then: no advice and no alarm colour, only why it stopped
        assertThat(view.rows()).singleElement().satisfies(row -> {
            assertThat(row.settled()).isTrue();
            assertThat(row.problem()).isNull();
            assertThat(row.statusTone()).isEqualTo("is-neutral");
            assertThat(row.outcome()).isEqualTo("a point-of-sale sale without the customer's email");
        });
        verify(alerts, never()).pageProblem(any(), any(), any());
    }

    @Test
    void aLiveAttemptIsNeverSettledByTheOrdersDocument() {
        // given: fiscalised with its e-mail failed; the order is invoiced by that very receipt
        ReceiptAttempt attempt = failedEmailAttempt();

        // when
        ReceiptOrderView view = ReceiptOrderView.of(List.of(attempt), false, true, alerts, NOW, Locale.ENGLISH);

        // then: the e-mail still has to go out
        assertThat(view.rows()).singleElement().satisfies(row -> {
            assertThat(row.settled()).isFalse();
            assertThat(row.statusTone()).isEqualTo("is-ok");
            assertThat(row.canResendEmail()).isTrue();
        });
    }

    @Test
    void aDeadAttemptOfACancelledOrderIsSettled() {
        // given: the e-receipt failed, then the order was cancelled; "Wystaw ponownie" is gone with the sale
        ReceiptAttempt failed = deadAttempt("order-1:R1", ReceiptAttemptState.FAILED, 1);
        when(alerts.outcome(failed, Locale.ENGLISH)).thenReturn("invalid VAT rate");
        Order cancelled = ReceiptFixtures.b2cOrder(100);
        cancelled.setStatus(OrderStatus.Cancelled);

        // when
        ReceiptOrderView view = ReceiptOrderView.of(List.of(failed), false,
                ReceiptTrigger.settlesDeadAttempts(cancelled), alerts, NOW, Locale.ENGLISH);

        // then: no advice pointing at a button the cancelled page does not have, no alarm colour
        assertThat(view.canReissue()).isFalse();
        assertThat(view.rows()).singleElement().satisfies(row -> {
            assertThat(row.settled()).isTrue();
            assertThat(row.problem()).isNull();
            assertThat(row.statusTone()).isEqualTo("is-neutral");
            assertThat(row.outcome()).isEqualTo("invalid VAT rate");
        });
    }
}
