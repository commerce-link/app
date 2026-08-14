package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierConnectionModeResolverTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private SupplierConnectionModeResolver resolver;

    private Store storeWithConnections(StoreSupplierConnection... connections) {
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setSupplierConnections(List.of(connections));
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);
        return store;
    }

    @Test
    void resolvesEachConnectionModeByProviderName() {
        // given
        Store store = storeWithConnections(
                new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL),
                new StoreSupplierConnection("Kosatec", ConnectionMode.OWN),
                new StoreSupplierConnection("Acme", ConnectionMode.MANUAL));

        // when / then
        assertThat(resolver.resolve(store, "Elko")).isEqualTo(ConnectionMode.GLOBAL);
        assertThat(resolver.resolve(store, "Kosatec")).isEqualTo(ConnectionMode.OWN);
        assertThat(resolver.resolve(store, "Acme")).isEqualTo(ConnectionMode.MANUAL);
    }

    @Test
    void returnsNullForAProviderTheStoreHasNoConnectionTo() {
        // given
        Store store = storeWithConnections(new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL));

        // when / then
        assertThat(resolver.resolve(store, "IngramMicro")).isNull();
    }

    @Test
    void returnsNullForAMissingStore() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertThat(resolver.resolve(STORE_ID, "Elko")).isNull();
    }

    @Test
    void loadsTheStoreWhenResolvingByStoreId() {
        // given
        when(storesRepository.findById(STORE_ID))
                .thenReturn(storeWithConnections(new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL)));

        // when / then
        assertThat(resolver.resolve(STORE_ID, "Elko")).isEqualTo(ConnectionMode.GLOBAL);
    }
}
