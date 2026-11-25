package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.starter.storage.FileStorage;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSupplierFeedServiceTest {

    private static final String BUCKET = "feeds";
    private static final String FEED_KEY = "stub-feed.csv";
    private static final String HASH_KEY = "feed-hashes/stub.sha256";
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private SecretsManager secrets;
    @Mock
    private FileStorage fileStorage;

    private GlobalSupplierFeedService service() {
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));
        return new GlobalSupplierFeedService(new GlobalSupplierProviderFactory(factory, secrets),
                new InventoryRepository(fileStorage, BUCKET), CLOCK);
    }

    private void lastDownload(String feed, Instant storedAt) {
        when(fileStorage.findLastModified(BUCKET, HASH_KEY)).thenReturn(Optional.of(storedAt));
        when(fileStorage.findLastModified(BUCKET, FEED_KEY)).thenReturn(Optional.of(storedAt));
        when(fileStorage.getBytes(BUCKET, HASH_KEY)).thenReturn(hashOf(feed));
    }

    private static byte[] hashOf(String feed) {
        return FeedHash.of(feed.getBytes(), "csv").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void loadFeedDecodesSingleValueGlobalSecretAndStores() throws ResourceDownloadException {
        // given
        when(secrets.getSecret("Stub")).thenReturn("feed");

        // when
        service().loadFeed("Stub");

        // then
        verify(fileStorage).put(BUCKET, FEED_KEY, "feed".getBytes());
    }

    @Test
    void loadFeedSkipsAnUnchangedFeedYoungerThanADay() throws ResourceDownloadException {
        // given
        when(secrets.getSecret("Stub")).thenReturn("feed");
        lastDownload("feed", NOW.minus(Duration.ofHours(23)));

        // when
        service().loadFeed("Stub");

        // then
        verify(fileStorage, never()).put(eq(BUCKET), eq(FEED_KEY), any(byte[].class));
    }

    @Test
    void loadFeedStoresAnUnchangedFeedAgainOnceItIsADayOld() throws ResourceDownloadException {
        // given
        when(secrets.getSecret("Stub")).thenReturn("feed");
        lastDownload("feed", NOW.minus(Duration.ofHours(24)));

        // when
        service().loadFeed("Stub");

        // then
        verify(fileStorage).put(BUCKET, FEED_KEY, "feed".getBytes());
    }

    @Test
    void loadFeedDecodesJsonGlobalSecretAndStores() throws ResourceDownloadException {
        // given
        when(secrets.getSecret("Stub")).thenReturn("{\"url\":\"feed\"}");

        // when
        service().loadFeed("Stub");

        // then
        verify(fileStorage).put(BUCKET, FEED_KEY, "feed".getBytes());
    }

    @Test
    void loadFeedDoesNothingForUnknownSupplier() throws ResourceDownloadException {
        // when
        service().loadFeed("Unknown");

        // then
        verifyNoInteractions(fileStorage);
    }

    @Test
    void loadFeedTreatsBlankGlobalSecretAsEmptyConfig() throws ResourceDownloadException {
        // given
        when(secrets.getSecret("Stub")).thenReturn("  ");

        // when
        service().loadFeed("Stub");

        // then
        verify(fileStorage).put(BUCKET, FEED_KEY, "x".getBytes());
    }

    @Test
    void loadFeedFailsLoudlyOnMalformedJsonGlobalSecret() {
        // given
        when(secrets.getSecret("Stub")).thenReturn("{not-valid-json");

        // when / then
        assertThatThrownBy(() -> service().loadFeed("Stub")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(fileStorage);
    }
}
