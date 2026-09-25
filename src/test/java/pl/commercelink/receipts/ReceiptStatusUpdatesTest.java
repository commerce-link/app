package pl.commercelink.receipts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.receipts.api.FiscalData;
import pl.commercelink.receipts.api.Receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class ReceiptStatusUpdatesTest {

    private final InMemoryReceiptAttemptStore attempts = new InMemoryReceiptAttemptStore();
    private final ReceiptWorkPublisher publisher = mock(ReceiptWorkPublisher.class);
    private final MutableClock clock = MutableClock.at("2026-09-23T13:00:00Z");
    private final ReceiptStatusUpdates updates = new ReceiptStatusUpdates(attempts, publisher, clock);

    @BeforeEach
    void setUp() {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("s1");
        attempt.setReceiptKey("o1:R1");
        attempt.setProvider("test-receipts");
        attempt.setState(ReceiptAttemptState.PENDING);
        attempt.setProviderReceiptId("p1");
        attempts.create(attempt);
    }

    private static Receipt fiscalised(String key) {
        return Receipt.fiscalised(key, "p1", new FiscalData(null, null, java.time.Instant.parse("2026-09-23T12:59:00Z")), "https://x/1");
    }

    @Test
    void aFiscalisationIsAppliedAndTheAttemptWokenForItsEffects() {
        updates.applyPushed("s1", "test-receipts", fiscalised("o1:R1"));

        ReceiptAttempt attempt = attempts.find("s1", "o1:R1").orElseThrow();
        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.FISCALISED);
        assertThat(attempt.getNextCheckAt()).isEqualTo(clock.instant());
        verify(publisher).publishNow("s1", "o1:R1");
    }

    @Test
    void unmatchedResultsAreIgnoredQuietly() {
        updates.applyPushed("s1", "test-receipts", fiscalised("unknown:R1"));
        updates.applyPushed("s1", "test-receipts", Receipt.pending(null, "p1"));
        updates.applyPushed("s2", "test-receipts", fiscalised("o1:R1"));
        updates.applyPushed("s1", "other-provider", fiscalised("o1:R1"));

        assertThat(attempts.find("s1", "o1:R1").orElseThrow().getState()).isEqualTo(ReceiptAttemptState.PENDING);
        verifyNoInteractions(publisher);
    }

    @Test
    void neverThrows() {
        InMemoryReceiptAttemptStore broken = new InMemoryReceiptAttemptStore() {
            @Override
            public java.util.Optional<ReceiptAttempt> find(String storeId, String receiptKey) {
                throw new RuntimeException("dynamo down");
            }
        };

        assertThatCode(() -> new ReceiptStatusUpdates(broken, publisher, clock)
                .applyPushed("s1", "test-receipts", fiscalised("o1:R1"))).doesNotThrowAnyException();
    }

    @Test
    void aConcurrentApplyOfTheSameStateWinningTheRaceDoesNotWakeTheAttemptAgain() {
        // given: another thread (e.g. a poll or a second webhook delivery) fiscalises the same attempt with the
        // same outcome first, and wins the version race on save()
        attempts.interleaveOnce(other -> {
            ReceiptStatusMerger.merge(other, fiscalised("o1:R1"), ReceiptStatusMerger.Source.PUSH, clock.instant());
            other.schedule(clock.instant());
        });

        // when: this call's own save() conflicts, update() re-reads and re-applies the predicate; the second run
        // finds the attempt already FISCALISED with the same result, so the merge is a no-op
        updates.applyPushed("s1", "test-receipts", fiscalised("o1:R1"));

        // then: exactly the interleaved write's effects were published, not a second one from this call — the
        // bug would leave changed[0] = true from the first (discarded) predicate run and publish anyway
        ReceiptAttempt attempt = attempts.find("s1", "o1:R1").orElseThrow();
        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.FISCALISED);
        verifyNoInteractions(publisher);
    }
}
