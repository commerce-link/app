package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryRepositoryTest {

    private static final Instant STORED_AT = Instant.parse("2026-11-24T07:55:18Z");

    @Mock
    private FileStorage fileStorage;

    private InventoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InventoryRepository(fileStorage, "feeds");
    }

    @Test
    void hashIsKeptOutsideTheFeedNames() {
        // when
        repository.storeHash("Acadia", "abc123");

        // then
        verify(fileStorage).put("feeds", "feed-hashes/acadia.sha256", "abc123".getBytes());
    }

    @Test
    void storedHashIsReadWhenWrittenAfterTheFeed() {
        // given
        when(fileStorage.findLastModified("feeds", "feed-hashes/acadia.sha256")).thenReturn(Optional.of(STORED_AT.plusSeconds(1)));
        when(fileStorage.findLastModified("feeds", "acadia-feed.csv")).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.getBytes("feeds", "feed-hashes/acadia.sha256")).thenReturn("abc123\n".getBytes());

        // when / then
        assertThat(repository.storedHash("Acadia", "csv")).contains(new InventoryRepository.StoredHash("abc123", STORED_AT));
    }

    @Test
    void storedHashHoldsForAFeedWrittenInTheSameSecond() {
        // given
        when(fileStorage.findLastModified("feeds", "feed-hashes/acadia.sha256")).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.findLastModified("feeds", "acadia-feed.csv")).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.getBytes("feeds", "feed-hashes/acadia.sha256")).thenReturn("abc123".getBytes());

        // when / then
        assertThat(repository.storedHash("Acadia", "csv")).contains(new InventoryRepository.StoredHash("abc123", STORED_AT));
    }

    @Test
    void storedHashIsIgnoredWhenTheFeedWasWrittenAfterIt() {
        // given
        when(fileStorage.findLastModified("feeds", "feed-hashes/acadia.sha256")).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.findLastModified("feeds", "acadia-feed.csv")).thenReturn(Optional.of(STORED_AT.plusSeconds(60)));

        // when
        Optional<InventoryRepository.StoredHash> hash = repository.storedHash("Acadia", "csv");

        // then
        assertThat(hash).isEmpty();
        verify(fileStorage, never()).getBytes(anyString(), anyString());
    }

    @Test
    void storedHashIsIgnoredWhenTheFeedIsMissing() {
        // given
        when(fileStorage.findLastModified("feeds", "feed-hashes/acadia.sha256")).thenReturn(Optional.of(STORED_AT));
        when(fileStorage.findLastModified("feeds", "acadia-feed.xml")).thenReturn(Optional.empty());

        // when / then
        assertThat(repository.storedHash("Acadia", "xml")).isEmpty();
    }

    @Test
    void feedWithoutAHashHasNone() {
        // given
        when(fileStorage.findLastModified("feeds", "feed-hashes/acadia.sha256")).thenReturn(Optional.empty());

        // when / then
        assertThat(repository.storedHash("Acadia", "csv")).isEmpty();
    }

    @Test
    void reloadReadsTheDatesOfTheFeedFilesOnly() {
        // given
        LocalDateTime stored = LocalDateTime.of(2026, 11, 24, 8, 55);
        when(fileStorage.getAllObjectLastModified("feeds", "")).thenReturn(Map.of(
                "acadia-feed.csv", stored,
                "feed-hashes/acadia.sha256", stored.plusHours(4)));

        // when / then
        assertThat(repository.getLatestModifiedPerSupplier()).containsExactly(Map.entry("acadia", stored));
    }

    @Test
    void lastDownloadIsTheLaterOfTheFeedAndItsHash() {
        // given
        LocalDateTime stored = LocalDateTime.of(2026, 11, 24, 8, 55);
        when(fileStorage.getAllObjectLastModified("feeds", "")).thenReturn(Map.of(
                "acadia-feed.csv", stored,
                "feed-hashes/acadia.sha256", stored.plusHours(4),
                "morele-feed.csv", stored,
                "feed-hashes/morele.sha256", stored.minusHours(1)));

        // when
        Map<String, LocalDateTime> lastDownloaded = repository.getLastDownloadedPerSupplier();

        // then
        assertThat(lastDownloaded).containsOnly(
                Map.entry("acadia", stored.plusHours(4)),
                Map.entry("morele", stored));
    }

    @Test
    void hashWithoutItsFeedIsNoDownload() {
        // given
        when(fileStorage.getAllObjectLastModified("feeds", "")).thenReturn(Map.of(
                "feed-hashes/acadia.sha256", LocalDateTime.of(2026, 11, 24, 8, 55)));

        // when / then
        assertThat(repository.getLastDownloadedPerSupplier()).isEmpty();
    }
}
