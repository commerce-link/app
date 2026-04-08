package pl.commercelink.products;

import org.springframework.stereotype.Service;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrphanedProductCleanupService {

    private final StoresRepository storesRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;

    public OrphanedProductCleanupService(StoresRepository storesRepository,
                                         ProductCatalogRepository productCatalogRepository,
                                         ProductRepository productRepository) {
        this.storesRepository = storesRepository;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
    }

    public int cleanupOrphanedProducts() {
        Set<String> validCategoryIds = collectValidCategoryIds();
        System.out.println("[OrphanedProductCleanup] Valid category IDs: " + validCategoryIds.size());

        if (validCategoryIds.isEmpty()) {
            throw new RuntimeException("No valid category IDs found across any store. Aborting to prevent accidental full table deletion.");
        }

        List<Product> allProducts = productRepository.scanAllKeys();
        System.out.println("[OrphanedProductCleanup] Total products in table: " + allProducts.size());

        List<Product> orphaned = allProducts.stream()
                .filter(p -> !validCategoryIds.contains(p.getCategoryId()))
                .toList();

        System.out.println("[OrphanedProductCleanup] Orphaned products found: " + orphaned.size());

        if (!orphaned.isEmpty()) {
            productRepository.batchDelete(orphaned);
            System.out.println("[OrphanedProductCleanup] Deleted " + orphaned.size() + " orphaned products");
        }

        return orphaned.size();
    }

    private Set<String> collectValidCategoryIds() {
        List<Store> stores = storesRepository.findAll();

        return stores.stream()
                .flatMap(store -> productCatalogRepository.findAll(store.getStoreId()).stream())
                .flatMap(catalog -> catalog.getCategories().stream())
                .map(CategoryDefinition::getCategoryId)
                .collect(Collectors.toSet());
    }
}
