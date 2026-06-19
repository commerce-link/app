package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V004MigrateEnabledProvidersToSupplierConnectionsTest {

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private V004_MigrateEnabledProvidersToSupplierConnections migration;

    private Store storeWith(FulfilmentConfiguration config) {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void enablesGlobalSuppliersAndMigratesProvidersForExistingStores() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledProviders(List.of("Action", "Wortmann"));
        Store store = storeWith(config);
        when(storesRepository.findAll()).thenReturn(List.of(store));

        // when
        migration.migrate();

        // then
        ArgumentCaptor<Store> savedStore = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(savedStore.capture());
        FulfilmentConfiguration savedConfig = savedStore.getValue().getFulfilmentConfiguration();
        assertTrue(savedConfig.isCanUseGlobalSuppliers());
        List<String> supplierNames = savedConfig.getSupplierConnections().stream()
                .map(StoreSupplierConnection::getSupplierName)
                .toList();
        assertEquals(List.of("Action", "Wortmann"), supplierNames);
        assertTrue(savedConfig.getSupplierConnections().stream()
                .allMatch(c -> c.getMode() == ConnectionMode.GLOBAL));
    }

    @Test
    void doesNotOverwriteExistingConnectionsButStillEnablesGlobal() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledProviders(List.of("Action", "Wortmann"));
        List<StoreSupplierConnection> existing = new ArrayList<>(List.of(
                new StoreSupplierConnection("Elko", ConnectionMode.OWN)));
        config.setSupplierConnections(existing);
        Store store = storeWith(config);
        when(storesRepository.findAll()).thenReturn(List.of(store));

        // when
        migration.migrate();

        // then
        assertTrue(config.isCanUseGlobalSuppliers());
        assertEquals(1, config.getSupplierConnections().size());
        assertEquals("Elko", config.getSupplierConnections().get(0).getSupplierName());
        assertEquals(ConnectionMode.OWN, config.getSupplierConnections().get(0).getMode());
        verify(storesRepository).save(store);
    }

    @Test
    void migratingTwiceDoesNotDuplicateConnections() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledProviders(List.of("Action", "Wortmann"));
        Store store = storeWith(config);
        when(storesRepository.findAll()).thenReturn(List.of(store));

        // when
        migration.migrate();
        migration.migrate();

        // then
        assertTrue(config.isCanUseGlobalSuppliers());
        assertEquals(2, config.getSupplierConnections().size());
        List<String> supplierNames = config.getSupplierConnections().stream()
                .map(StoreSupplierConnection::getSupplierName)
                .toList();
        assertEquals(List.of("Action", "Wortmann"), supplierNames);
        verify(storesRepository, times(2)).save(store);
    }

    @Test
    void migratesMultipleStoresInOneRun() {
        // given
        FulfilmentConfiguration firstConfig = new FulfilmentConfiguration();
        firstConfig.setEnabledProviders(List.of("Action"));
        Store firstStore = new Store();
        firstStore.setStoreId("store-1");
        firstStore.setFulfilmentConfiguration(firstConfig);
        FulfilmentConfiguration secondConfig = new FulfilmentConfiguration();
        secondConfig.setEnabledProviders(List.of("Wortmann"));
        Store secondStore = new Store();
        secondStore.setStoreId("store-2");
        secondStore.setFulfilmentConfiguration(secondConfig);
        when(storesRepository.findAll()).thenReturn(List.of(firstStore, secondStore));

        // when
        migration.migrate();

        // then
        assertTrue(firstConfig.isCanUseGlobalSuppliers());
        assertEquals(List.of("Action"), firstConfig.getSupplierConnections().stream()
                .map(StoreSupplierConnection::getSupplierName)
                .toList());
        assertTrue(secondConfig.isCanUseGlobalSuppliers());
        assertEquals(List.of("Wortmann"), secondConfig.getSupplierConnections().stream()
                .map(StoreSupplierConnection::getSupplierName)
                .toList());
        verify(storesRepository).save(firstStore);
        verify(storesRepository).save(secondStore);
    }

    @Test
    void enablesGlobalSuppliersEvenWhenNoProvidersConfigured() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        Store store = storeWith(config);
        when(storesRepository.findAll()).thenReturn(List.of(store));

        // when
        migration.migrate();

        // then
        assertTrue(config.isCanUseGlobalSuppliers());
        assertTrue(config.getSupplierConnections().isEmpty());
        verify(storesRepository).save(store);
    }

    @Test
    void skipsStoresWithoutFulfilmentConfiguration() {
        // given
        Store store = new Store();
        store.setStoreId("store-2");
        when(storesRepository.findAll()).thenReturn(List.of(store));

        // when
        migration.migrate();

        // then
        verify(storesRepository, never()).save(any());
    }
}
