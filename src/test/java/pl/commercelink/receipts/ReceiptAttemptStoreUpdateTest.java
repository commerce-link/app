package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptAttemptStoreUpdateTest {

    private final InMemoryReceiptAttemptStore store = new InMemoryReceiptAttemptStore();

    private ReceiptAttempt attempt(String key) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("s1");
        attempt.setReceiptKey(key);
        attempt.setOrderId("o1");
        attempt.setState(ReceiptAttemptState.ISSUING);
        return attempt;
    }

    @Test
    void createRefusesAnExistingKey() {
        assertThat(store.create(attempt("o1:R1"))).isTrue();
        assertThat(store.create(attempt("o1:R1"))).isFalse();
    }

    @Test
    void updateRetriesAfterAConcurrentWrite() {
        store.create(attempt("o1:R1"));
        store.interleaveOnce(stored -> stored.setLastError("written by someone else"));

        ReceiptAttempt updated = store.update("s1", "o1:R1", a -> {
            a.setIssueCalls(a.getIssueCalls() + 1);
            return true;
        }).orElseThrow();

        assertThat(updated.getIssueCalls()).isEqualTo(1);
        assertThat(updated.getLastError()).isEqualTo("written by someone else");
    }

    @Test
    void unchangedAttemptIsNotWritten() {
        store.create(attempt("o1:R1"));
        long before = store.find("s1", "o1:R1").orElseThrow().getVersion();

        store.update("s1", "o1:R1", a -> false);

        assertThat(store.find("s1", "o1:R1").orElseThrow().getVersion()).isEqualTo(before);
    }

    @Test
    void updateWrittenIsEmptyWhenTheChangeDeclines() {
        store.create(attempt("o1:R1"));
        long before = store.find("s1", "o1:R1").orElseThrow().getVersion();

        assertThat(store.updateWritten("s1", "o1:R1", a -> false)).isEmpty();
        assertThat(store.find("s1", "o1:R1").orElseThrow().getVersion()).isEqualTo(before);
    }

    @Test
    void updateWrittenIsEmptyForAMissingAttempt() {
        assertThat(store.updateWritten("s1", "o1:R1", a -> true)).isEmpty();
    }

    @Test
    void updateWrittenReturnsTheSavedAttempt() {
        store.create(attempt("o1:R1"));

        ReceiptAttempt written = store.updateWritten("s1", "o1:R1", a -> {
            a.setIssueCalls(a.getIssueCalls() + 1);
            return true;
        }).orElseThrow();

        assertThat(written.getIssueCalls()).isEqualTo(1);
        assertThat(store.find("s1", "o1:R1").orElseThrow().getIssueCalls()).isEqualTo(1);
        assertThat(written.getVersion()).isEqualTo(store.find("s1", "o1:R1").orElseThrow().getVersion());
    }

    @Test
    void updateWrittenIsEmptyWhenTheRetryAfterAConflictDeclines() {
        store.create(attempt("o1:R1"));
        // the concurrent write makes the change unnecessary: the retry sees it and declines
        store.interleaveOnce(stored -> stored.setState(ReceiptAttemptState.FAILED));

        assertThat(store.updateWritten("s1", "o1:R1", a -> {
            if (a.getState() != ReceiptAttemptState.ISSUING) {
                return false;
            }
            a.setIssueCalls(a.getIssueCalls() + 1);
            return true;
        })).isEmpty();
        ReceiptAttempt stored = store.find("s1", "o1:R1").orElseThrow();
        assertThat(stored.getState()).isEqualTo(ReceiptAttemptState.FAILED);
        assertThat(stored.getIssueCalls()).isZero();
    }

    @Test
    void updateStillReturnsTheUnchangedAttemptWhenTheChangeDeclines() {
        store.create(attempt("o1:R1"));

        assertThat(store.update("s1", "o1:R1", a -> false)).isPresent();
    }

    @Test
    void updateWrittenLetsAnExceptionFromTheChangePropagate() {
        store.create(attempt("o1:R1"));
        IllegalStateException refused = new IllegalStateException("refused");

        assertThatThrownBy(() -> store.updateWritten("s1", "o1:R1", a -> {
            throw refused;
        })).isSameAs(refused);
    }

    @Test
    void dueAttemptsComeInScheduleOrderAndSkipUnscheduled() {
        ReceiptAttempt later = attempt("o1:R1");
        later.schedule(Instant.parse("2026-09-23T10:05:00Z"));
        ReceiptAttempt sooner = attempt("o2:R1");
        sooner.schedule(Instant.parse("2026-09-23T10:01:00Z"));
        ReceiptAttempt idle = attempt("o3:R1");
        store.create(later);
        store.create(sooner);
        store.create(idle);

        assertThat(store.findDue(Instant.parse("2026-09-23T10:10:00Z"), 10))
                .extracting(ReceiptAttempt::getReceiptKey).containsExactly("o2:R1", "o1:R1");
        assertThat(store.findDue(Instant.parse("2026-09-23T10:02:00Z"), 10))
                .extracting(ReceiptAttempt::getReceiptKey).containsExactly("o2:R1");
    }

    @Test
    void attemptsOfAnOrderAreOrderedByNumber() {
        ReceiptAttempt second = attempt("o1:R2");
        second.setAttemptNo(2);
        ReceiptAttempt first = attempt("o1:R1");
        first.setAttemptNo(1);
        ReceiptAttempt other = attempt("o10:R1");
        other.setOrderId("o10");
        store.create(second);
        store.create(first);
        store.create(other);

        assertThat(store.findByOrder("s1", "o1")).extracting(ReceiptAttempt::getReceiptKey)
                .containsExactly("o1:R1", "o1:R2");
    }
}
