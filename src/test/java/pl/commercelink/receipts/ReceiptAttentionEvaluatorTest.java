package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptAttentionEvaluatorTest {

    private static ReceiptAttempt attempt(ReceiptAttemptState state, Instant createdAt) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setState(state);
        attempt.setCreatedAt(createdAt);
        return attempt;
    }

    @Test
    void unknownOutcomeAlertsAfterSixIssueCalls() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.ISSUING, now);
        attempt.setIssueCalls(5);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isNull();
        attempt.setIssueCalls(6);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isEqualTo(ReceiptAttention.ISSUING_UNKNOWN);
    }

    @Test
    void invalidRequestAfterSendingAlertsAtOnce() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.ISSUING, now);
        attempt.setIssueCalls(2);
        attempt.setInvalidAfterSend(true);

        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isEqualTo(ReceiptAttention.INVALID_AFTER_SEND);
    }

    @Test
    void pendingAlertsAfterTwoDays() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.PENDING, now.minus(Duration.ofHours(47))), now)).isNull();
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.PENDING, now.minus(Duration.ofHours(49))), now))
                .isEqualTo(ReceiptAttention.PENDING_LONG);
    }

    @Test
    void pendingNearTheEndOfTheMonthInWarsawAlertsEarly() {
        Instant now = Instant.parse("2026-09-28T23:30:00Z");   // 29 September, 01:30 in Warsaw

        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.PENDING, now.minus(Duration.ofHours(2))), now))
                .isEqualTo(ReceiptAttention.PENDING_MONTH_END);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.PENDING, now.minus(Duration.ofMinutes(10))), now))
                .isNull();
    }

    @Test
    void providerUnavailableAlertsAfterThirtyMinutesWithNoIssueCallYet() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.ISSUING, now.minus(Duration.ofMinutes(31)));
        attempt.setLastErrorAt(now.minus(Duration.ofMinutes(5)));

        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isEqualTo(ReceiptAttention.PROVIDER_UNAVAILABLE);
    }

    @Test
    void providerUnavailableNeverAlertsBeforeThirtyMinutesOrWithoutAnErrorOrOnceIssueWasCalled() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");

        ReceiptAttempt tooSoon = attempt(ReceiptAttemptState.ISSUING, now.minus(Duration.ofMinutes(10)));
        tooSoon.setLastErrorAt(now);
        assertThat(ReceiptAttentionEvaluator.evaluate(tooSoon, now)).isNull();

        ReceiptAttempt noErrorYet = attempt(ReceiptAttemptState.ISSUING, now.minus(Duration.ofMinutes(45)));
        assertThat(ReceiptAttentionEvaluator.evaluate(noErrorYet, now)).isNull();

        ReceiptAttempt oldEnoughButSent = attempt(ReceiptAttemptState.ISSUING, now.minus(Duration.ofMinutes(45)));
        oldEnoughButSent.setLastErrorAt(now);
        oldEnoughButSent.setIssueCalls(1);
        assertThat(ReceiptAttentionEvaluator.evaluate(oldEnoughButSent, now)).isNull();
    }

    @Test
    void terminalProblemsAlert() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        ReceiptAttempt noLink = attempt(ReceiptAttemptState.FISCALISED, now);
        noLink.setLinkGaveUpAt(now);
        ReceiptAttempt noMail = attempt(ReceiptAttemptState.FISCALISED, now);
        noMail.setEmailClaimedAt(now);

        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.FAILED, now), now)).isEqualTo(ReceiptAttention.FAILED);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.BLOCKED, now), now)).isEqualTo(ReceiptAttention.BLOCKED);
        assertThat(ReceiptAttentionEvaluator.evaluate(noLink, now)).isEqualTo(ReceiptAttention.LINK_MISSING);
        assertThat(ReceiptAttentionEvaluator.evaluate(noMail, now)).isEqualTo(ReceiptAttention.EMAIL_NOT_SENT);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt(ReceiptAttemptState.CLOSED_MANUALLY, now), now)).isNull();
    }

    @Test
    void effectsFailuresAlertAfterThreeAndTakePrecedenceOverEmailAndLink() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.FISCALISED, now);
        attempt.setEffectsFailures(2);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isNull();

        attempt.setEffectsFailures(3);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isEqualTo(ReceiptAttention.EFFECTS_FAILED);

        // takes precedence over both EMAIL_NOT_SENT and LINK_MISSING
        attempt.setEmailClaimedAt(now);
        attempt.setLinkGaveUpAt(now);
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, now)).isEqualTo(ReceiptAttention.EFFECTS_FAILED);
    }

    @Test
    void aSkippedEmailNeverAlertsEvenThoughItWasClaimed() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        ReceiptAttempt skipped = attempt(ReceiptAttemptState.FISCALISED, now);
        skipped.setEmailClaimedAt(now);
        skipped.setEmailSkippedAt(now);

        assertThat(ReceiptAttentionEvaluator.evaluate(skipped, now)).isNull();
    }
}
