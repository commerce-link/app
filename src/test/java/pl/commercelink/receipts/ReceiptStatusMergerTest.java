package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.receipts.api.FiscalData;
import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptFailure;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.receipts.ReceiptStatusMerger.Source.*;

class ReceiptStatusMergerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final Instant T_FISCAL = Instant.parse("2026-09-23T11:59:00Z");

    private static ReceiptAttempt attempt(ReceiptAttemptState state) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey("o1:R1");
        attempt.setState(state);
        return attempt;
    }

    private static Receipt fiscalised(String url) {
        return Receipt.fiscalised("o1:R1", "p1", new FiscalData(null, "7", T_FISCAL), url);
    }

    @Test
    void issueResultPendingMovesIssuingToPending() {
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.ISSUING);

        assertThat(ReceiptStatusMerger.merge(attempt, Receipt.pending("o1:R1", "p1"), ISSUE, NOW)).isTrue();

        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.PENDING);
        assertThat(attempt.getProviderReceiptId()).isEqualTo("p1");
    }

    @Test
    void pendingFromAWebhookOrPollNeverTakesAwayTheIssueRetry() {
        ReceiptAttempt pushed = attempt(ReceiptAttemptState.ISSUING);
        ReceiptAttempt polled = attempt(ReceiptAttemptState.ISSUING);

        ReceiptStatusMerger.merge(pushed, Receipt.pending("o1:R1", "p1"), PUSH, NOW);
        ReceiptStatusMerger.merge(polled, Receipt.pending("o1:R1", "p1"), POLL, NOW);

        assertThat(pushed.getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(polled.getState()).isEqualTo(ReceiptAttemptState.ISSUING);
    }

    @Test
    void fiscalisationFromAnySourceIsFinal() {
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.ISSUING);

        ReceiptStatusMerger.merge(attempt, fiscalised(null), PUSH, NOW);
        boolean changed = ReceiptStatusMerger.merge(attempt, Receipt.pending("o1:R1", "p1"), ISSUE, NOW);

        assertThat(changed).isFalse();
        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.FISCALISED);
        assertThat(attempt.getFiscalisedAt()).isEqualTo(T_FISCAL);
        assertThat(attempt.getReceiptNumber()).isEqualTo("7");
    }

    @Test
    void theFirstFiscalisationMomentIsKeptAndALateLinkIsAdded() {
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.PENDING);
        ReceiptStatusMerger.merge(attempt, fiscalised(null), POLL, NOW);

        Receipt later = Receipt.fiscalised(null, "p1", new FiscalData(null, "7", NOW), "https://x/1");
        assertThat(ReceiptStatusMerger.merge(attempt, later, POLL, NOW)).isTrue();

        assertThat(attempt.getFiscalisedAt()).isEqualTo(T_FISCAL);
        assertThat(attempt.getDocumentUrl()).isEqualTo("https://x/1");
    }

    @Test
    void failureEndsALiveAttemptButNeverAFiscalisedOne() {
        ReceiptAttempt pending = attempt(ReceiptAttemptState.PENDING);
        ReceiptAttempt done = attempt(ReceiptAttemptState.PENDING);
        ReceiptStatusMerger.merge(done, fiscalised("u"), POLL, NOW);
        Receipt failed = Receipt.failed("o1:R1", "p1", new ReceiptFailure("fiscal_error", "VAT"));

        ReceiptStatusMerger.merge(pending, failed, POLL, NOW);
        ReceiptStatusMerger.merge(done, failed, POLL, NOW);

        assertThat(pending.getState()).isEqualTo(ReceiptAttemptState.FAILED);
        assertThat(pending.getFailureCode()).isEqualTo("fiscal_error");
        assertThat(done.getState()).isEqualTo(ReceiptAttemptState.FISCALISED);
    }

    @Test
    void closedAndDeadAttemptsIgnoreResults() {
        ReceiptAttempt closed = attempt(ReceiptAttemptState.CLOSED_MANUALLY);
        ReceiptAttempt blocked = attempt(ReceiptAttemptState.BLOCKED);

        assertThat(ReceiptStatusMerger.merge(closed, fiscalised("u"), PUSH, NOW)).isFalse();
        assertThat(ReceiptStatusMerger.merge(blocked, fiscalised("u"), PUSH, NOW)).isFalse();
    }

    @Test
    void resultsOfAnotherProviderReceiptAreIgnored() {
        ReceiptAttempt attempt = attempt(ReceiptAttemptState.PENDING);
        attempt.setProviderReceiptId("p1");

        assertThat(ReceiptStatusMerger.merge(attempt,
                Receipt.fiscalised("o1:R1", "p2", new FiscalData(null, null, NOW), "u"), PUSH, NOW)).isFalse();
        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.PENDING);
    }
}
