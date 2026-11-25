package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.StoreInventoryProvider;
import pl.commercelink.inventory.StoreInventoryRefreshPublisher;
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
    private final StoreInventoryRefreshPublisher storeInventoryRefresh;

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
        boolean stored = storeFeedRepository.hashedFeed(storeId, supplierName, feed.extension())
                .storeIfChanged(feed, () -> storeInventoryRefresh.request(storeId));
        if (!stored && !storeInventoryProvider.keepIfRecent(storeId)) {
            storeInventoryRefresh.request(storeId);
        }
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
