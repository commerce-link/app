package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Service
public class StoreSupplierFeedService {

    private static final int CONFIGURATION_WAIT_ATTEMPTS = 5;
    private static final long CONFIGURATION_WAIT_MILLIS = 2000;

    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final StoreFeedRepository storeFeedRepository;
    private final Sleeper sleeper;

    @Autowired
    public StoreSupplierFeedService(StoresRepository storesRepository,
                                    SupplierProviderFactory supplierProviderFactory,
                                    StoreFeedRepository storeFeedRepository) {
        this(storesRepository, supplierProviderFactory, storeFeedRepository, Thread::sleep);
    }

    StoreSupplierFeedService(StoresRepository storesRepository,
                             SupplierProviderFactory supplierProviderFactory,
                             StoreFeedRepository storeFeedRepository,
                             Sleeper sleeper) {
        this.storesRepository = storesRepository;
        this.supplierProviderFactory = supplierProviderFactory;
        this.storeFeedRepository = storeFeedRepository;
        this.sleeper = sleeper;
    }

    public void loadStoreFeed(String storeId, String supplierName) throws ResourceDownloadException {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return;
        }
        awaitRequiredConfiguration(store, supplierName);
        SupplierProvider supplier = supplierProviderFactory.get(store, supplierName);
        if (supplier == null) {
            return;
        }
        supplier.download().ifPresent(feedData ->
                storeFeedRepository.store(storeId, supplierName, feedData.data(), feedData.extension()));
    }

    private void awaitRequiredConfiguration(Store store, String supplierName) throws ResourceDownloadException {
        SupplierProviderDescriptor descriptor = supplierProviderFactory.getDescriptor(supplierName);
        if (descriptor == null || descriptor.configurationFields().stream().noneMatch(ProviderField::required)) {
            return;
        }
        for (int attempt = 1; attempt <= CONFIGURATION_WAIT_ATTEMPTS; attempt++) {
            if (!supplierProviderFactory.loadConfiguration(store, supplierName).isEmpty()) {
                return;
            }
            if (attempt < CONFIGURATION_WAIT_ATTEMPTS) {
                waitForConfiguration(store, supplierName);
            }
        }
        throw new ResourceDownloadException(
                "Configuration for supplier " + supplierName + " of store " + store.getStoreId()
                        + " is not readable yet", null);
    }

    private void waitForConfiguration(Store store, String supplierName) throws ResourceDownloadException {
        try {
            sleeper.sleep(CONFIGURATION_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceDownloadException(
                    "Interrupted while waiting for configuration of supplier " + supplierName
                            + " of store " + store.getStoreId(), e);
        }
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
