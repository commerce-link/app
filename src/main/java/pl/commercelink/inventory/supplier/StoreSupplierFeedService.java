package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.StoreInventoryProvider;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Service
@RequiredArgsConstructor
public class StoreSupplierFeedService {

    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final StoreFeedRepository storeFeedRepository;
    private final StoreInventoryProvider storeInventoryProvider;

    public void loadStoreFeed(String storeId, String supplierName) throws ResourceDownloadException {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new SupplierFeedTargetMissingException(storeId, supplierName, "the store does not exist");
        }
        requireReadableConfiguration(store, supplierName);
        SupplierProvider supplier = supplierProviderFactory.get(store, supplierName);
        if (supplier == null) {
            throw new SupplierFeedTargetMissingException(storeId, supplierName, "no provider is registered for this supplier");
        }
        supplier.download().ifPresent(feed -> storeIfChanged(storeId, supplierName, feed));
    }

    private void storeIfChanged(String storeId, String supplierName, FeedData feed) {
        String hash = FeedHash.of(feed.data(), feed.extension());
        if (storeFeedRepository.storedHash(storeId, supplierName).filter(hash::equals).isPresent()) {
            storeInventoryProvider.keepOrRefresh(storeId);
            storeFeedRepository.storeHash(storeId, supplierName, hash);
            return;
        }
        storeFeedRepository.store(storeId, supplierName, feed.data(), feed.extension());
        // a failed rebuild fails the import, so the queue delivers it again; the previous snapshot stays until then
        storeInventoryProvider.refresh(storeId);
        // stored last: an import that stopped before the rebuild must not be taken for unchanged when it is delivered again
        storeFeedRepository.storeHash(storeId, supplierName, hash);
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
