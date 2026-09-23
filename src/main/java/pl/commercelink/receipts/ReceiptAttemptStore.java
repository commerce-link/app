package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Persistence of receipt attempts. Every write after creation is conditional on the version read. */
public interface ReceiptAttemptStore {

    int MAX_UPDATE_ATTEMPTS = 10;

    Optional<ReceiptAttempt> find(String storeId, String receiptKey);

    /** Attempts of one order by attempt number. */
    List<ReceiptAttempt> findByOrder(String storeId, String orderId);

    /** Creates the attempt; false when the key already exists (nothing written). */
    boolean create(ReceiptAttempt attempt);

    /** Saves a loaded attempt; throws {@link ConditionalCheckFailedException} when someone wrote it meanwhile. */
    void save(ReceiptAttempt attempt);

    /** Attempts due at {@code now}, soonest first. */
    List<ReceiptAttempt> findDue(Instant now, int limit);

    /** Whether the store has attempts that still need the provider (ISSUING or PENDING). */
    boolean hasLiveAttempts(String storeId);

    /**
     * Loads, applies {@code change} and saves, retrying on a concurrent write. {@code change} returns whether it
     * modified the attempt; an unmodified attempt is not written. Empty when the attempt does not exist.
     */
    default Optional<ReceiptAttempt> update(String storeId, String receiptKey, Predicate<ReceiptAttempt> change) {
        for (int i = 0; i < MAX_UPDATE_ATTEMPTS; i++) {
            Optional<ReceiptAttempt> loaded = find(storeId, receiptKey);
            if (loaded.isEmpty()) {
                return Optional.empty();
            }
            ReceiptAttempt attempt = loaded.get();
            if (!change.test(attempt)) {
                return Optional.of(attempt);
            }
            try {
                save(attempt);
                return Optional.of(attempt);
            } catch (ConditionalCheckFailedException e) {
                // someone wrote the attempt meanwhile: re-read and apply again
            }
        }
        throw new IllegalStateException("Receipt attempt " + receiptKey + " kept changing; update given up");
    }
}
