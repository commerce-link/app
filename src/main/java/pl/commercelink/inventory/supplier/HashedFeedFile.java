package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.starter.storage.FileStorage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A supplier feed in storage with the hash of its last download in a file of its own. A download equal to that hash is
 * not stored, so the feed file keeps its date and nothing reads it again; only the hash is written again, which keeps
 * the date of the last download moving.
 */
@RequiredArgsConstructor
public final class HashedFeedFile {

    private final FileStorage fileStorage;
    private final String bucketName;
    private final String feedKey;
    private final String hashKey;

    /**
     * Stores the feed unless it equals the last download, and runs {@code afterStore} once it is stored.
     *
     * @return whether the feed was stored
     */
    public boolean storeIfChanged(FeedData feed, Runnable afterStore) {
        return storeIfChanged(feed, stored -> true, afterStore);
    }

    /**
     * Stores the feed unless it equals the last download and {@code keepStored} accepts the copy already stored.
     *
     * @return whether the feed was stored
     */
    public boolean storeIfChanged(FeedData feed, Predicate<StoredHash> keepStored) {
        return storeIfChanged(feed, keepStored, () -> {
        });
    }

    private boolean storeIfChanged(FeedData feed, Predicate<StoredHash> keepStored, Runnable afterStore) {
        String hash = FeedHash.of(feed.data(), feed.extension());
        boolean changed = storedHash()
                .filter(stored -> stored.hash().equals(hash) && keepStored.test(stored))
                .isEmpty();
        if (changed) {
            fileStorage.put(bucketName, feedKey, feed.data());
            afterStore.run();
        }
        // written last: the hash says that everything a changed feed needs is done, so a download that stopped before
        // afterStore finished must not be taken for unchanged when it comes again
        fileStorage.put(bucketName, hashKey, hash.getBytes(StandardCharsets.UTF_8));
        return changed;
    }

    // A hash written before the feed file (the file put by hand, or by a version that keeps no hash) no longer describes
    // the stored file.
    private Optional<StoredHash> storedHash() {
        Optional<Instant> hashedAt = fileStorage.findLastModified(bucketName, hashKey);
        if (hashedAt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> storedAt = fileStorage.findLastModified(bucketName, feedKey);
        if (storedAt.isEmpty() || storedAt.get().isAfter(hashedAt.get())) {
            return Optional.empty();
        }
        String hash = new String(fileStorage.getBytes(bucketName, hashKey), StandardCharsets.UTF_8).trim();
        return Optional.of(new StoredHash(hash, storedAt.get()));
    }

    /** @param feedStoredAt when the feed file the hash describes was stored */
    public record StoredHash(String hash, Instant feedStoredAt) {
    }
}
