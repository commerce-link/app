package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GlobalSupplierFeedService {

    // A reload converts the feed's prices with the exchange rates of its day, so a feed unchanged for longer is stored
    // again, for the instances to reload it with the current ones.
    static final Duration MAX_AGE_OF_KEPT_FEED = Duration.ofHours(24);

    private final GlobalSupplierProviderFactory globalSupplierProviderFactory;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    @Autowired
    public GlobalSupplierFeedService(GlobalSupplierProviderFactory globalSupplierProviderFactory,
                                     InventoryRepository inventoryRepository) {
        this(globalSupplierProviderFactory, inventoryRepository, Clock.systemUTC());
    }

    public void loadFeed(String supplierName) throws ResourceDownloadException {
        Optional<SupplierProvider> provider = globalSupplierProviderFactory.get(supplierName);
        if (provider.isEmpty()) {
            return;
        }
        provider.get().download().ifPresent(feed -> storeIfChanged(supplierName, feed));
    }

    // An unchanged feed keeps its file and with it the file's date, so no instance parses and regroups it again.
    private void storeIfChanged(String supplierName, FeedData feed) {
        Instant keptSince = clock.instant().minus(MAX_AGE_OF_KEPT_FEED);
        inventoryRepository.hashedFeed(supplierName, feed.extension())
                .storeIfChanged(feed, stored -> stored.feedStoredAt().isAfter(keptSince));
    }
}
