package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.InventoryRepository.StoredHash;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalSupplierFeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private GlobalSupplierFeedService serviceFor(SecretsManager secrets, InventoryRepository inventory) {
        return serviceFor(secrets, inventory, Clock.systemUTC());
    }

    private GlobalSupplierFeedService serviceFor(SecretsManager secrets, InventoryRepository inventory, Clock clock) {
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));
        return new GlobalSupplierFeedService(new GlobalSupplierProviderFactory(factory, secrets), inventory, clock);
    }

    @Test
    void loadFeedDecodesSingleValueGlobalSecretAndStores() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("feed");
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Stub");

        // then
        ArgumentCaptor<byte[]> data = ArgumentCaptor.forClass(byte[].class);
        verify(inventory).store(eq("Stub"), data.capture(), eq("csv"));
        assertThat(new String(data.getValue())).isEqualTo("feed");
    }

    @Test
    void loadFeedSkipsAnUnchangedFeedAndWritesItsHashAgain() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        String hash = FeedHash.of("feed".getBytes(), "csv");
        when(secrets.getSecret("Stub")).thenReturn("feed");
        when(inventory.storedHash("Stub", "csv")).thenReturn(Optional.of(new StoredHash(hash, NOW.minus(Duration.ofHours(23)))));
        GlobalSupplierFeedService service = serviceFor(secrets, inventory, CLOCK);

        // when
        service.loadFeed("Stub");

        // then
        verify(inventory, never()).store(anyString(), any(byte[].class), anyString());
        verify(inventory).storeHash("Stub", hash);
    }

    @Test
    void loadFeedStoresAnUnchangedFeedAgainOnceItIsADayOld() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        String hash = FeedHash.of("feed".getBytes(), "csv");
        when(secrets.getSecret("Stub")).thenReturn("feed");
        when(inventory.storedHash("Stub", "csv")).thenReturn(Optional.of(new StoredHash(hash, NOW.minus(Duration.ofHours(24)))));
        GlobalSupplierFeedService service = serviceFor(secrets, inventory, CLOCK);

        // when
        service.loadFeed("Stub");

        // then
        InOrder order = inOrder(inventory);
        order.verify(inventory).store(eq("Stub"), any(byte[].class), eq("csv"));
        order.verify(inventory).storeHash("Stub", hash);
    }

    @Test
    void loadFeedStoresAChangedFeedBeforeItsHash() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("feed");
        when(inventory.storedHash("Stub", "csv"))
                .thenReturn(Optional.of(new StoredHash(FeedHash.of("older feed".getBytes(), "csv"), NOW.minus(Duration.ofHours(1)))));
        GlobalSupplierFeedService service = serviceFor(secrets, inventory, CLOCK);

        // when
        service.loadFeed("Stub");

        // then
        InOrder order = inOrder(inventory);
        order.verify(inventory).store(eq("Stub"), any(byte[].class), eq("csv"));
        order.verify(inventory).storeHash("Stub", FeedHash.of("feed".getBytes(), "csv"));
    }

    @Test
    void loadFeedStoresAFeedWithoutAStoredHashBeforeItsHash() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("feed");
        when(inventory.storedHash("Stub", "csv")).thenReturn(Optional.empty());
        GlobalSupplierFeedService service = serviceFor(secrets, inventory, CLOCK);

        // when
        service.loadFeed("Stub");

        // then
        InOrder order = inOrder(inventory);
        order.verify(inventory).store(eq("Stub"), any(byte[].class), eq("csv"));
        order.verify(inventory).storeHash("Stub", FeedHash.of("feed".getBytes(), "csv"));
    }

    @Test
    void loadFeedDecodesJsonGlobalSecretAndStores() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("{\"url\":\"feed\"}");
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Stub");

        // then
        ArgumentCaptor<byte[]> data = ArgumentCaptor.forClass(byte[].class);
        verify(inventory).store(eq("Stub"), data.capture(), eq("csv"));
        assertThat(new String(data.getValue())).isEqualTo("feed");
    }

    @Test
    void loadFeedDoesNothingForUnknownSupplier() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Unknown");

        // then
        verify(inventory, never()).store(anyString(), any(byte[].class), anyString());
    }

    @Test
    void loadFeedTreatsBlankGlobalSecretAsEmptyConfig() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("  ");
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when
        service.loadFeed("Stub");

        // then
        ArgumentCaptor<byte[]> data = ArgumentCaptor.forClass(byte[].class);
        verify(inventory).store(eq("Stub"), data.capture(), eq("csv"));
        assertThat(new String(data.getValue())).isEqualTo("x");
    }

    @Test
    void loadFeedFailsLoudlyOnMalformedJsonGlobalSecret() {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(secrets.getSecret("Stub")).thenReturn("{not-valid-json");
        GlobalSupplierFeedService service = serviceFor(secrets, inventory);

        // when / then
        assertThatThrownBy(() -> service.loadFeed("Stub")).isInstanceOf(RuntimeException.class);
    }
}
