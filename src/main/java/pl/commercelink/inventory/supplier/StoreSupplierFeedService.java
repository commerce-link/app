package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreSupplierFeedService {

    private final StoresRepository storesRepository;
    private final SupplierConfigurationManager configurationManager;
    private final SupplierRegistry supplierRegistry;
    private final StoreFeedRepository storeFeedRepository;

    public void loadStoreFeed(String storeId, String supplierName) throws ResourceDownloadException {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return;
        }
        Map<String, String> config = configurationManager.loadConfiguration(store, supplierName);
        Optional<FeedData> feed = supplierRegistry.downloadFeed(supplierName, config);
        feed.ifPresent(feedData ->
                storeFeedRepository.store(storeId, supplierName, feedData.data(), feedData.extension()));
    }
}
