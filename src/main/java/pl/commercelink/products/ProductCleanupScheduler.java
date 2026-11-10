package pl.commercelink.products;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class ProductCleanupScheduler {

    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final StoresRepository storesRepository;

    @Value("${product.cleanup.days:7}")
    private int cleanupDays;

    @SqsListener(
            value = "product-cleanup-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void cleanUpOrphanedProducts(String message) {
        LocalDateTime thresholdDate = LocalDateTime.now().minusDays(cleanupDays);
        List<Store> stores = storesRepository.findAll();
        for (Store store : stores) {
            List<ProductCatalog> catalogs = productCatalogRepository.findAll(store.getStoreId());

            for (ProductCatalog catalog : catalogs) {
                List<CategoryDefinition> categoriesToCleanup = catalog.getCategories().stream()
                        .filter(category -> isPastRetention(category, thresholdDate))
                        .toList();

                for (CategoryDefinition category : categoriesToCleanup) {
                    // Products first, then the category again: a switch to manual saves the type before it stores the
                    // products, so a product read here that the switch stored is always seen with the manual type below.
                    List<Product> productsToDelete = productRepository.findAll(category.getCategoryId());
                    if (!productsToDelete.isEmpty() && stillPastRetention(catalog, category, thresholdDate)) {
                        // Whatever their version: a product saved since the read (moved onto a clone, say) still belongs
                        // to a category automatic past the retention, and a version conflict would fail the whole run.
                        productsToDelete.forEach(productRepository::deleteWhateverItsVersion);
                    }
                }
            }
        }
    }

    /** The run's read of the catalogs may be minutes old by now: the category could have turned manual or been removed. */
    private boolean stillPastRetention(ProductCatalog catalog, CategoryDefinition category, LocalDateTime thresholdDate) {
        ProductCatalog fresh = productCatalogRepository.findByIdConsistently(catalog.getStoreId(), catalog.getCatalogId());
        return fresh != null && fresh.getCategories().stream()
                .filter(current -> Objects.equals(current.getCategoryId(), category.getCategoryId()))
                .anyMatch(current -> isPastRetention(current, thresholdDate));
    }

    private static boolean isPastRetention(CategoryDefinition category, LocalDateTime thresholdDate) {
        return category.getType() == CategoryDefinitionType.Dynamic
                && category.getTypeChangedAt() != null
                && category.getTypeChangedAt().isBefore(thresholdDate);
    }
}
