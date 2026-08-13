package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GlobalSupplierFeedService {

    private final GlobalSupplierProviderFactory globalSupplierProviderFactory;
    private final InventoryRepository inventoryRepository;

    public void loadFeed(String supplierName) throws ResourceDownloadException {
        Optional<SupplierProvider> provider = globalSupplierProviderFactory.get(supplierName);
        if (provider.isEmpty()) {
            return;
        }
        provider.get().download().ifPresent(feedData ->
                inventoryRepository.store(supplierName, feedData.data(), feedData.extension()));
    }
}
