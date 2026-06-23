package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierRegistryTest {

    private SupplierRegistry registryFor(SecretsManager secrets) {
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));
        return new SupplierRegistry(factory, secrets);
    }

    @Test
    void downloadFeedDecodesSingleValueGlobalSecretIntoConfigMap() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        when(secrets.getSecret("Stub")).thenReturn("feed");
        SupplierRegistry registry = registryFor(secrets);

        // when
        Optional<FeedData> feed = registry.downloadFeed("Stub");

        // then
        assertThat(feed).isPresent();
        assertThat(new String(feed.get().data())).isEqualTo("feed");
    }

    @Test
    void downloadFeedDecodesJsonGlobalSecretIntoConfigMap() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        when(secrets.getSecret("Stub")).thenReturn("{\"url\":\"feed\"}");
        SupplierRegistry registry = registryFor(secrets);

        // when
        Optional<FeedData> feed = registry.downloadFeed("Stub");

        // then
        assertThat(feed).isPresent();
        assertThat(new String(feed.get().data())).isEqualTo("feed");
    }

    @Test
    void downloadFeedReturnsEmptyForUnknownSupplier() throws ResourceDownloadException {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        SupplierRegistry registry = registryFor(secrets);

        // when
        Optional<FeedData> feed = registry.downloadFeed("Unknown");

        // then
        assertThat(feed).isEmpty();
    }
}
