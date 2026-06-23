package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Supplier;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        Supplier supplier = factory.get(store, "Stub");

        // then
        assertThat(supplier).isNotNull();
        assertThat(new String(supplier.download().orElseThrow().data())).isEqualTo("feed");
    }
}
