package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.GlobalSupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Component
@RequiredArgsConstructor
public class StoreSupplierProviderResolver {

    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final GlobalSupplierProviderFactory globalSupplierProviderFactory;

    public SupplierProvider resolve(String storeId, String provider) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return null;
        }
        if (store.isOwnSupplier(provider)) {
            return supplierProviderFactory.get(store, provider);
        }
        if (store.isGlobalSupplier(provider)) {
            return globalSupplierProviderFactory.get(provider).orElse(null);
        }
        return null;
    }
}
