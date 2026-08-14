package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Component
@RequiredArgsConstructor
public class SupplierConnectionModeResolver {

    private final StoresRepository storesRepository;

    public ConnectionMode resolve(String storeId, String provider) {
        return resolve(storesRepository.findById(storeId), provider);
    }

    public ConnectionMode resolve(Store store, String provider) {
        if (store == null || provider == null) {
            return null;
        }
        if (store.isGlobalSupplier(provider)) {
            return ConnectionMode.GLOBAL;
        }
        if (store.isOwnSupplier(provider)) {
            return ConnectionMode.OWN;
        }
        if (store.isManualSupplier(provider)) {
            return ConnectionMode.MANUAL;
        }
        return null;
    }
}
