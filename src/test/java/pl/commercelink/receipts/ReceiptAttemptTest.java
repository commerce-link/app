package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code needsProvider()} decides {@link ReceiptAttemptStore#hasLiveAttempts}: still polled, or still waiting for
 *  its link, means the receipt system cannot be switched or disconnected out from under it. */
class ReceiptAttemptTest {

    private static ReceiptAttempt attempt(ReceiptAttemptState state, String documentUrl, Instant linkGaveUpAt) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setState(state);
        attempt.setDocumentUrl(documentUrl);
        attempt.setLinkGaveUpAt(linkGaveUpAt);
        return attempt;
    }

    @Test
    void issuingAndPendingAlwaysNeedTheProvider() {
        assertThat(attempt(ReceiptAttemptState.ISSUING, null, null).needsProvider()).isTrue();
        assertThat(attempt(ReceiptAttemptState.ISSUING, "https://x", Instant.now()).needsProvider()).isTrue();
        assertThat(attempt(ReceiptAttemptState.PENDING, null, null).needsProvider()).isTrue();
        assertThat(attempt(ReceiptAttemptState.PENDING, "https://x", Instant.now()).needsProvider()).isTrue();
    }

    @Test
    void fiscalisedNeedsTheProviderOnlyWhileNeitherTheLinkNorTheGiveUpHappened() {
        assertThat(attempt(ReceiptAttemptState.FISCALISED, null, null).needsProvider()).isTrue();
        assertThat(attempt(ReceiptAttemptState.FISCALISED, "https://x", null).needsProvider()).isFalse();
        assertThat(attempt(ReceiptAttemptState.FISCALISED, null, Instant.now()).needsProvider()).isFalse();
        assertThat(attempt(ReceiptAttemptState.FISCALISED, "https://x", Instant.now()).needsProvider()).isFalse();
    }

    @Test
    void deadOrManuallyClosedAttemptsNeverNeedTheProvider() {
        assertThat(attempt(ReceiptAttemptState.FAILED, null, null).needsProvider()).isFalse();
        assertThat(attempt(ReceiptAttemptState.BLOCKED, null, null).needsProvider()).isFalse();
        assertThat(attempt(ReceiptAttemptState.CLOSED_MANUALLY, null, null).needsProvider()).isFalse();
    }
}
