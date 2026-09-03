package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierProviderResolverTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";

    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private GlobalSupplierProviderFactory globalSupplierProviderFactory;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierProvider supplierProvider;

    @InjectMocks
    private SupplierProviderResolver resolver;

    private Store storeWithConnection(String provider, ConnectionMode mode) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setSupplierConnections(List.of(
                new StoreSupplierConnection(provider, mode, true, true)));
        store.setFulfilmentConfiguration(configuration);
        return store;
    }

    @Test
    void manualConnectionReturnsNull() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.MANUAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertNull(resolver.resolve(STORE_ID, PROVIDER));
        verifyNoInteractions(supplierProviderFactory, globalSupplierProviderFactory);
    }

    @Test
    void ownConnectionReturnsTheOwnFactoryProvider() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.OWN);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(supplierProviderFactory.get(store, PROVIDER)).thenReturn(supplierProvider);

        // when
        SupplierProvider result = resolver.resolve(STORE_ID, PROVIDER);

        // then
        assertSame(supplierProvider, result);
        verifyNoInteractions(globalSupplierProviderFactory);
    }

    @Test
    void globalConnectionReturnsTheGlobalFactoryProvider() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(globalSupplierProviderFactory.get(PROVIDER)).thenReturn(Optional.of(supplierProvider));

        // when
        SupplierProvider result = resolver.resolve(STORE_ID, PROVIDER);

        // then
        assertSame(supplierProvider, result);
        verifyNoInteractions(supplierProviderFactory);
    }

    @Test
    void globalConnectionWithNoProviderRegisteredReturnsNull() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.GLOBAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(globalSupplierProviderFactory.get(PROVIDER)).thenReturn(Optional.empty());

        // when / then
        assertNull(resolver.resolve(STORE_ID, PROVIDER));
    }

    @Test
    void returnsNullWhenTheStoreDoesNotExist() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertNull(resolver.resolve(STORE_ID, PROVIDER));
        verifyNoInteractions(supplierProviderFactory);
        verifyNoInteractions(globalSupplierProviderFactory);
    }

    @Test
    void returnsNullWhenTheSupplierIsNeitherOwnNorGlobal() {
        // given
        Store store = storeWithConnection(PROVIDER, ConnectionMode.MANUAL);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertNull(resolver.resolve(STORE_ID, PROVIDER));
        verifyNoInteractions(supplierProviderFactory);
        verifyNoInteractions(globalSupplierProviderFactory);
    }
}
