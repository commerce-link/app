package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierProviderFactoryTest {

    private Store storeWithId(String id) {
        Store store = new Store();
        store.setStoreId(id);
        return store;
    }

    @Test
    void getConstructsSupplierFromStoreConfiguration() throws Exception {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        Store store = storeWithId("store-1");
        when(secrets.exists("store-1-stub")).thenReturn(true);
        when(secrets.getSecret("store-1-stub", java.util.Map.class)).thenReturn(java.util.Map.of("url", "feed"));

        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));

        // when
        SupplierProvider supplier = factory.get(store, "Stub");

        // then
        assertThat(supplier).isNotNull();
        assertThat(new String(supplier.download().orElseThrow().data())).isEqualTo("feed");
    }

    @Test
    void getResolvesTheDescriptorByTypeAndTheSecretByIdentity() throws Exception {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        Store store = storeWithId("store-1");
        when(secrets.exists("store-1-stub-k7f3a9c2")).thenReturn(true);
        when(secrets.getSecret("store-1-stub-k7f3a9c2", java.util.Map.class)).thenReturn(java.util.Map.of("url", "feed-b"));
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));

        // when
        SupplierProvider supplier = factory.get(store, "Stub-k7f3a9c2");

        // then
        assertThat(supplier).isNotNull();
        assertThat(new String(supplier.download().orElseThrow().data())).isEqualTo("feed-b");
        assertThat(factory.getDescriptor("Stub-k7f3a9c2")).isSameAs(factory.getDescriptor("Stub"));
    }

    @Test
    void deleteConfigurationRemovesTheSecretOfTheIdentityNotOfTheType() {
        // given -- two instances of one type share the descriptor but never the secret
        SecretsManager secrets = mock(SecretsManager.class);
        Store store = storeWithId("store-1");
        when(secrets.exists("store-1-stub-k7f3a9c2")).thenReturn(true);
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));

        // when
        factory.deleteConfiguration(store, "Stub-k7f3a9c2");

        // then
        verify(secrets).deleteSecret("store-1-stub-k7f3a9c2");
        verify(secrets, never()).deleteSecret("store-1-stub");
    }

    @Test
    void saveConfigurationWritesTheSecretOfTheIdentityNotOfTheType() {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        Store store = storeWithId("store-1");
        when(secrets.exists("store-1-stub-k7f3a9c2")).thenReturn(false);
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));
        java.util.Map<String, String> config = java.util.Map.of("url", "feed-b");

        // when
        factory.saveConfiguration(store, "Stub-k7f3a9c2", config);

        // then
        verify(secrets).createSecret("store-1-stub-k7f3a9c2", config);
        verify(secrets, never()).createSecret(eq("store-1-stub"), any());
    }

    @Test
    void loadConfigurationOfAnUnknownTypeFallsBackToTheRawName() {
        // given
        SecretsManager secrets = mock(SecretsManager.class);
        Store store = storeWithId("store-1");
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(secrets));

        // when
        java.util.Map<String, String> config = factory.loadConfiguration(store, "Nope-k7f3a9c2");

        // then
        assertThat(config).isEmpty();
        verify(secrets).exists("store-1-nope-k7f3a9c2");
    }
}
