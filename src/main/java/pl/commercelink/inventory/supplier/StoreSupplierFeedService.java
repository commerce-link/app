package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Service;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.util.Map;
import java.util.Optional;

@Service
public class StoreSupplierFeedService {

    private final StoresRepository storesRepository;
    private final SupplierConfigurationManager configurationManager;
    private final SupplierRegistry supplierRegistry;
    private final InventoryRepository inventoryRepository;
    private final StoreInventoryCache storeInventoryCache;

    public StoreSupplierFeedService(StoresRepository storesRepository,
                                    SupplierConfigurationManager configurationManager,
                                    SupplierRegistry supplierRegistry,
                                    InventoryRepository inventoryRepository,
                                    StoreInventoryCache storeInventoryCache) {
        this.storesRepository = storesRepository;
        this.configurationManager = configurationManager;
        this.supplierRegistry = supplierRegistry;
        this.inventoryRepository = inventoryRepository;
        this.storeInventoryCache = storeInventoryCache;
    }

    public void loadStoreFeed(String storeId, String supplierName) throws ResourceDownloadException {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return;
        }
        Map<String, String> config = configurationManager.loadConfiguration(store, supplierName);
        Optional<FeedData> feed = supplierRegistry.downloadFeed(supplierName, config);
        feed.ifPresent(feedData -> {
            inventoryRepository.store(storeId, supplierName, feedData.data(), feedData.extension());
            storeInventoryCache.invalidate(storeId);
        });
    }
}
