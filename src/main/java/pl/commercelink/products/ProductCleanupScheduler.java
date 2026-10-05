package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class ProductCleanupScheduler {

    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final StoresRepository storesRepository;

    @Value("${product.cleanup.days:7}")
    private int cleanupDays;

    @Autowired
    public ProductCleanupScheduler(ProductCatalogRepository productCatalogRepository, 
                                   ProductRepository productRepository,
                                   StoresRepository storesRepository) {
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.storesRepository = storesRepository;
    }

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
                        .filter(category -> category.getType() == CategoryDefinitionType.Dynamic)
                        .filter(category -> category.getTypeChangedAt() != null)
                        .filter(category -> category.getTypeChangedAt().isBefore(thresholdDate))
                        .collect(Collectors.toList());
                
                for (CategoryDefinition category : categoriesToCleanup) {
                    List<Product> productsToDelete = productRepository.findAll(category.getCategoryId());
                    if (!productsToDelete.isEmpty()) {
                        productRepository.delete(productsToDelete);
                    }
                }
            }
        }
    }
}