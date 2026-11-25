package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.starter.storage.FileStorage;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryRepositoryTest {

    @Mock
    private FileStorage fileStorage;

    private InventoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InventoryRepository(fileStorage, "feeds");
    }

    @Test
    void feedAndItsHashAreKeptUnderTheirOwnNames() {
        // given
        FeedData feed = new FeedData("rows".getBytes(), "csv");

        // when
        repository.hashedFeed("Acadia", "csv").storeIfChanged(feed, stored -> true);

        // then
        verify(fileStorage).findLastModified("feeds", "feed-hashes/acadia.sha256");
        verify(fileStorage).put("feeds", "acadia-feed.csv", feed.data());
        verify(fileStorage).put(eq("feeds"), eq("feed-hashes/acadia.sha256"), any(byte[].class));
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
