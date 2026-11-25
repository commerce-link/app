package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.HashedFeedFile.StoredHash;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.starter.storage.FileStorage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HashedFeedFileTest {

    private static final String BUCKET = "feeds";
    private static final String FEED_KEY = "acadia-feed.csv";
    private static final String HASH_KEY = "feed-hashes/acadia.sha256";
    private static final Instant STORED_AT = Instant.parse("2026-11-24T07:55:18Z");
    private static final FeedData FEED = new FeedData("rows".getBytes(), "csv");
    private static final String HASH = FeedHash.of(FEED.data(), FEED.extension());

    @Mock
    private FileStorage fileStorage;
    @Mock
    private Runnable afterStore;

    private HashedFeedFile file;

    @BeforeEach
    void setUp() {
        file = new HashedFeedFile(fileStorage, BUCKET, FEED_KEY, HASH_KEY);
    }

    private void lastDownload(String hash, Instant hashedAt, Instant feedStoredAt) {
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.of(hashedAt));
        when(fileStorage.findLastModified(BUCKET, FEED_KEY)).thenReturn(Optional.of(feedStoredAt));
        when(fileStorage.getBytes(BUCKET, HASH_KEY)).thenReturn((hash + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void firstDownloadStoresTheFeedRunsAfterStoreThenWritesTheHash() {
        // given
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.empty());

        // when
        boolean stored = file.storeIfChanged(FEED, afterStore);

        // then
        assertThat(stored).isTrue();
        InOrder order = inOrder(fileStorage, afterStore);
        order.verify(fileStorage).put(BUCKET, FEED_KEY, FEED.data());
        order.verify(afterStore).run();
        order.verify(fileStorage).put(BUCKET, HASH_KEY, HASH.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void unchangedFeedWritesOnlyItsHash() {
        // given
        lastDownload(HASH, STORED_AT.plusSeconds(1), STORED_AT);

        // when
        boolean stored = file.storeIfChanged(FEED, afterStore);

        // then
        assertThat(stored).isFalse();
        verify(fileStorage, never()).put(eq(BUCKET), eq(FEED_KEY), any(byte[].class));
        verify(afterStore, never()).run();
        verify(fileStorage).put(BUCKET, HASH_KEY, HASH.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void changedFeedIsStoredBeforeItsHash() {
        // given
        lastDownload(FeedHash.of("older rows".getBytes(), "csv"), STORED_AT.plusSeconds(1), STORED_AT);

        // when
        boolean stored = file.storeIfChanged(FEED, afterStore);

        // then
        assertThat(stored).isTrue();
        InOrder order = inOrder(fileStorage, afterStore);
        order.verify(fileStorage).put(BUCKET, FEED_KEY, FEED.data());
        order.verify(afterStore).run();
        order.verify(fileStorage).put(BUCKET, HASH_KEY, HASH.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void hashWrittenInTheSameSecondAsTheFeedStillDescribesIt() {
        // given
        lastDownload(HASH, STORED_AT, STORED_AT);

        // when / then
        assertThat(file.storeIfChanged(FEED, afterStore)).isFalse();
    }

    @Test
    void hashWrittenBeforeTheFeedIsIgnored() {
        // given
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.findLastModified(BUCKET, FEED_KEY)).thenReturn(Optional.of(STORED_AT.plusSeconds(60)));

        // when
        boolean stored = file.storeIfChanged(FEED, afterStore);

        // then
        assertThat(stored).isTrue();
        verify(fileStorage, never()).getBytes(anyString(), anyString());
    }

    @Test
    void hashWithoutItsFeedIsIgnored() {
        // given
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.findLastModified(BUCKET, FEED_KEY)).thenReturn(Optional.empty());

        // when / then
        assertThat(file.storeIfChanged(FEED, afterStore)).isTrue();
    }

    @Test
    void sameBytesUnderAnotherExtensionAreAChangedFeed() {
        // given
        lastDownload(FeedHash.of(FEED.data(), "xml"), STORED_AT.plusSeconds(1), STORED_AT);

        // when / then
        assertThat(file.storeIfChanged(FEED, afterStore)).isTrue();
    }

    @Test
    void storedCopyTheCallerDoesNotKeepIsStoredAgain() {
        // given
        lastDownload(HASH, STORED_AT.plusSeconds(1), STORED_AT);
        AtomicReference<StoredHash> offered = new AtomicReference<>();

        // when
        boolean stored = file.storeIfChanged(FEED, storedHash -> {
            offered.set(storedHash);
            return false;
        });

        // then
        assertThat(stored).isTrue();
        assertThat(offered.get()).isEqualTo(new StoredHash(HASH, STORED_AT));
        InOrder order = inOrder(fileStorage);
        order.verify(fileStorage).put(BUCKET, FEED_KEY, FEED.data());
        order.verify(fileStorage).put(BUCKET, HASH_KEY, HASH.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void afterStoreThatFailsLeavesTheHashUnwritten() {
        // given
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("queue unavailable")).when(afterStore).run();

        // when / then
        assertThatThrownBy(() -> file.storeIfChanged(FEED, afterStore)).isInstanceOf(IllegalStateException.class);
        verify(fileStorage).put(BUCKET, FEED_KEY, FEED.data());
        verify(fileStorage, never()).put(eq(BUCKET), eq(HASH_KEY), any(byte[].class));
    }
}
