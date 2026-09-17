package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreSupplierFeedService {

    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final StoreFeedRepository storeFeedRepository;

    public boolean loadStoreFeed(String storeId, String supplierName) throws ResourceDownloadException {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return false;
        }
        requireReadableConfiguration(store, supplierName);
        SupplierProvider supplier = supplierProviderFactory.get(store, supplierName);
        if (supplier == null) {
            return false;
        }
        Optional<FeedData> feed = supplier.download();
        feed.ifPresent(feedData -> storeFeedRepository.store(storeId, supplierName, feedData.data(), feedData.extension()));
        return feed.isPresent();
    }

    private void requireReadableConfiguration(Store store, String supplierName) {
        SupplierProviderDescriptor descriptor = supplierProviderFactory.getDescriptor(supplierName);
        if (descriptor == null || descriptor.configurationFields().stream().noneMatch(ProviderField::required)) {
            return;
        }
        if (supplierProviderFactory.loadConfiguration(store, supplierName).isEmpty()) {
            throw new SupplierConfigurationNotReadyException(store.getStoreId(), supplierName);
        }
    }
}
