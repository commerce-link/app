package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.GlobalSupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreSupplierProviderResolverTest {

    @Mock private StoresRepository storesRepository;
    @Mock private SupplierProviderFactory supplierProviderFactory;
    @Mock private GlobalSupplierProviderFactory globalSupplierProviderFactory;
    @Mock private Store store;
    @Mock private SupplierProvider supplierProvider;
    @InjectMocks private StoreSupplierProviderResolver resolver;

    @Test
    void resolvesOwnSupplierThroughStoreFactory() {
        // given
        when(storesRepository.findById("s1")).thenReturn(store);
        when(store.isOwnSupplier("Acme")).thenReturn(true);
        when(supplierProviderFactory.get(store, "Acme")).thenReturn(supplierProvider);

        // when / then
        assertSame(supplierProvider, resolver.resolve("s1", "Acme"));
    }

    @Test
    void resolvesGlobalSupplierThroughGlobalFactory() {
        // given
        when(storesRepository.findById("s1")).thenReturn(store);
        when(store.isOwnSupplier("IncomGroup")).thenReturn(false);
        when(store.isGlobalSupplier("IncomGroup")).thenReturn(true);
        when(globalSupplierProviderFactory.get("IncomGroup")).thenReturn(Optional.of(supplierProvider));

        // when / then
        assertSame(supplierProvider, resolver.resolve("s1", "IncomGroup"));
    }

    @Test
    void returnsNullForUnknownStoreOrConnection() {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when / then
        assertNull(resolver.resolve("missing", "Acme"));
    }
}
