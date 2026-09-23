package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Behaves like DynamoDB for the store's contract: copies in and out, version-conditional saves. */
public class InMemoryReceiptAttemptStore implements ReceiptAttemptStore {

    private static final ObjectMapper COPIER = JsonMapper.builder().findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();

    private final Map<String, ReceiptAttempt> items = new ConcurrentHashMap<>();
    private volatile Consumer<ReceiptAttempt> interleave;

    private static String id(String storeId, String key) {
        return storeId + "|" + key;
    }

    private static ReceiptAttempt copy(ReceiptAttempt attempt) {
        return COPIER.convertValue(attempt, ReceiptAttempt.class);
    }

    /** The next save first suffers a concurrent write by "someone else" doing {@code write}. */
    public void interleaveOnce(Consumer<ReceiptAttempt> write) {
        this.interleave = write;
    }

    public List<ReceiptAttempt> all() {
        return items.values().stream().map(InMemoryReceiptAttemptStore::copy).toList();
    }

    @Override
    public Optional<ReceiptAttempt> find(String storeId, String receiptKey) {
        return Optional.ofNullable(items.get(id(storeId, receiptKey))).map(InMemoryReceiptAttemptStore::copy);
    }

    @Override
    public List<ReceiptAttempt> findByOrder(String storeId, String orderId) {
        String prefix = ReceiptAttemptKeys.orderPrefix(orderId);
        return items.values().stream()
                .filter(a -> a.getStoreId().equals(storeId) && a.getReceiptKey().startsWith(prefix))
                .sorted(Comparator.comparingInt(ReceiptAttempt::getAttemptNo))
                .map(InMemoryReceiptAttemptStore::copy)
                .toList();
    }

    @Override
    public synchronized boolean create(ReceiptAttempt attempt) {
        String id = id(attempt.getStoreId(), attempt.getReceiptKey());
        if (items.containsKey(id)) {
            return false;
        }
        attempt.setVersion(1L);
        items.put(id, copy(attempt));
        return true;
    }

    @Override
    public synchronized void save(ReceiptAttempt attempt) {
        String id = id(attempt.getStoreId(), attempt.getReceiptKey());
        Consumer<ReceiptAttempt> write = interleave;
        if (write != null) {
            interleave = null;
            ReceiptAttempt other = copy(items.get(id));
            write.accept(other);
            other.setVersion(other.getVersion() + 1);
            items.put(id, other);
        }
        ReceiptAttempt stored = items.get(id);
        if (stored == null || !Objects.equals(stored.getVersion(), attempt.getVersion())) {
            throw new ConditionalCheckFailedException("version conflict on " + attempt.getReceiptKey());
        }
        attempt.setVersion(attempt.getVersion() + 1);
        items.put(id, copy(attempt));
    }

    @Override
    public List<ReceiptAttempt> findDue(Instant now, int limit) {
        return items.values().stream()
                .filter(a -> a.getNextCheckAt() != null && !a.getNextCheckAt().isAfter(now))
                .sorted(Comparator.comparing(ReceiptAttempt::getNextCheckAt))
                .limit(limit)
                .map(InMemoryReceiptAttemptStore::copy)
                .toList();
    }

    @Override
    public boolean hasLiveAttempts(String storeId) {
        return items.values().stream().anyMatch(a -> a.getStoreId().equals(storeId)
                && (a.getState() == ReceiptAttemptState.ISSUING || a.getState() == ReceiptAttemptState.PENDING));
    }
}
