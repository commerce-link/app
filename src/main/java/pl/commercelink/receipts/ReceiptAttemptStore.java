package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Persistence of receipt attempts. Every write after creation is conditional on the version read. */
public interface ReceiptAttemptStore {

    int MAX_UPDATE_ATTEMPTS = 10;

    /** Strongly consistent — updates and leases decide on it. */
    Optional<ReceiptAttempt> find(String storeId, String receiptKey);

    /** Attempts of one order by attempt number. */
    List<ReceiptAttempt> findByOrder(String storeId, String orderId);

    /** Creates the attempt; false when the key already exists (nothing written). */
    boolean create(ReceiptAttempt attempt);

    /** Saves a loaded attempt; throws {@link ConditionalCheckFailedException} when someone wrote it meanwhile. */
    void save(ReceiptAttempt attempt);

    /** Attempts due at {@code now}, soonest first. */
    List<ReceiptAttempt> findDue(Instant now, int limit);

    /**
     * Whether the store has attempts that still {@link ReceiptAttempt#needsProvider() need the provider}: ISSUING,
     * PENDING, or FISCALISED but still waiting for its link. "Live" here, unlike {@link ReceiptAttemptState#isLive()},
     * excludes a FISCALISED attempt whose link already arrived or was given up on — that one no longer needs the
     * provider, so switching or disconnecting the receipt system is safe for it.
     */
    boolean hasLiveAttempts(String storeId);

    /**
     * Loads, applies {@code change} and saves, retrying on a concurrent write. {@code change} returns whether it
     * modified the attempt; an unmodified attempt is not written. Empty when the attempt does not exist.
     */
    default Optional<ReceiptAttempt> update(String storeId, String receiptKey, Predicate<ReceiptAttempt> change) {
        return Optional.ofNullable(apply(this, storeId, receiptKey, change)).map(AppliedChange::attempt);
    }

    /**
     * Like {@link #update}, but present only when this call saved {@code change}'s modification: a predicate that
     * returned false on the final pass, or a missing attempt, gives empty. Retries on a version conflict re-run
     * {@code change} from a fresh read, so nothing the predicate did on an earlier, lost pass leaks into the result.
     */
    default Optional<ReceiptAttempt> updateWritten(String storeId, String receiptKey, Predicate<ReceiptAttempt> change) {
        return Optional.ofNullable(apply(this, storeId, receiptKey, change))
                .filter(AppliedChange::written)
                .map(AppliedChange::attempt);
    }

    /** The load-change-save loop behind both updates; null when the attempt does not exist. */
    private static AppliedChange apply(ReceiptAttemptStore store, String storeId, String receiptKey,
                                       Predicate<ReceiptAttempt> change) {
        for (int i = 0; i < MAX_UPDATE_ATTEMPTS; i++) {
            Optional<ReceiptAttempt> loaded = store.find(storeId, receiptKey);
            if (loaded.isEmpty()) {
                return null;
            }
            ReceiptAttempt attempt = loaded.get();
            if (!change.test(attempt)) {
                return new AppliedChange(attempt, false);
            }
            try {
                store.save(attempt);
                return new AppliedChange(attempt, true);
            } catch (ConditionalCheckFailedException e) {
                // someone wrote the attempt meanwhile: re-read and apply again
            }
        }
        throw new IllegalStateException("Receipt attempt " + receiptKey + " kept changing; update given up");
    }
}

/** The attempt an update ended with, and whether that update saved it. */
record AppliedChange(ReceiptAttempt attempt, boolean written) {
}
