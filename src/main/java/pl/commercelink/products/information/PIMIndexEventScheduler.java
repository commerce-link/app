package pl.commercelink.products.information;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.products.*;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
class PIMIndexEventScheduler {

    private final PimCatalog pimCatalog;

    private final ProductRepository productRepository;

    private final ProductCatalogRepository productCatalogRepository;

    private final StoresRepository storesRepository;

    private final Inventory inventory;

    private final ProductRecommendationEngine recommendationEngine;

    PIMIndexEventScheduler(PimCatalog pimCatalog, ProductRepository productRepository, ProductCatalogRepository productCatalogRepository,
                           StoresRepository storesRepository,
                           Inventory inventory, ProductRecommendationEngine recommendationEngine) {
        this.pimCatalog = pimCatalog;
        this.productRepository = productRepository;
        this.productCatalogRepository = productCatalogRepository;
        this.storesRepository = storesRepository;
        this.recommendationEngine = recommendationEngine;
        this.inventory = inventory;
    }

    @SqsListener(
            value = "product-pim-request-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void scanAndStoreProductsInPimQueue(String message) {
        storesRepository.findAll().stream()
                .map(Store::getStoreId)
                .map(this::scanAllProducts)
                .flatMap(Collection::stream)
                .forEach(pimCatalog::submit);
    }

    private Collection<PimEntryRequest> scanAllProducts(String storeId) {
        List<PimEntryRequest> events = new ArrayList<>();
        for (ProductCatalog productCatalog : productCatalogRepository.findAll(storeId)) {
            for (CategoryDefinition categoryDefinition : productCatalog.getCategories()) {
                events.addAll(getPimEntryRequests(storeId, categoryDefinition));
            }
        }
        return events;
    }

    private List<PimEntryRequest> getPimEntryRequests(String storeId, CategoryDefinition categoryDefinition) {
        List<Product> products = categoryDefinition.hasType(CategoryDefinitionType.Dynamic)
                ? getRecommendedProducts(storeId, categoryDefinition)
                : productRepository.findAll(categoryDefinition.getCategoryId());

        return products.stream()
                .filter(product -> pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode()).isEmpty())
                .map(p -> toPimEntryRequest(storeId, categoryDefinition.getType(), p))
                .collect(Collectors.toList());
    }

    private PimEntryRequest toPimEntryRequest(String storeId, CategoryDefinitionType catalogType, Product p) {
        MatchedInventory matchedInventory = inventory.withEnabledSuppliersOnly(storeId).findByProduct(p);
        int priority = PimQueuePriorityCalculator.calculatePriority(p.getBrand(), catalogType, matchedInventory);
        return new PimEntryRequest(p.getEan(), p.getManufacturerCode(), p.getBrand(), priority);
    }

    private List<Product> getRecommendedProducts(String storeId, CategoryDefinition categoryDefinition) {
        return recommendationEngine.getRecommendations(categoryDefinition, inventory.withEnabledSuppliersOnly(storeId))
                .stream()
                .map(ProductRecommendation::toProduct)
                .collect(Collectors.toList());
    }

}
