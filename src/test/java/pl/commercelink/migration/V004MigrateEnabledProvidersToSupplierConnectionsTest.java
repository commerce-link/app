package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V004MigrateEnabledProvidersToSupplierConnectionsTest {

    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final V004_MigrateEnabledProvidersToSupplierConnections migration =
            new V004_MigrateEnabledProvidersToSupplierConnections(storesRepository);

    private Store storeWith(FulfilmentConfiguration config) {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void enablesGlobalSuppliersAndMigratesProvidersForExistingStores() {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledProviders(List.of("Action", "Wortmann"));
        Store store = storeWith(config);
        when(storesRepository.findAll()).thenReturn(List.of(store));

        migration.migrate();

        assertTrue(config.isCanUseGlobalSuppliers());
        assertEquals(2, config.getSupplierConnections().size());
        assertTrue(config.getSupplierConnections().stream()
                .allMatch(c -> c.getMode() == ConnectionMode.GLOBAL));
        verify(storesRepository).save(store);
    }

    @Test
    void enablesGlobalSuppliersEvenWhenNoProvidersConfigured() {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        Store store = storeWith(config);
        when(storesRepository.findAll()).thenReturn(List.of(store));

        migration.migrate();

        assertTrue(config.isCanUseGlobalSuppliers());
        assertTrue(config.getSupplierConnections().isEmpty());
        verify(storesRepository).save(store);
    }

    @Test
    void skipsStoresWithoutFulfilmentConfiguration() {
        Store store = new Store();
        store.setStoreId("store-2");
        when(storesRepository.findAll()).thenReturn(List.of(store));

        migration.migrate();

        verify(storesRepository, never()).save(any());
    }
}
