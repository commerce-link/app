package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Component
@RequiredArgsConstructor
public class SupplierProviderResolver {

    private final SupplierProviderFactory supplierProviderFactory;
    private final GlobalSupplierProviderFactory globalSupplierProviderFactory;
    private final StoresRepository storesRepository;

    public SupplierProvider resolve(String storeId, String provider) {
        return resolve(storesRepository.findById(storeId), provider);
    }

    public SupplierProvider resolve(Store store, String provider) {
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
