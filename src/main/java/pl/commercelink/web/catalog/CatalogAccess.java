package pl.commercelink.web.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;

/** Loads catalog records for the store in the session; anything missing or belonging to another store is a 404, never a null that blows up in the template. */
@Component
@RequiredArgsConstructor
public class CatalogAccess {

    private final ProductCatalogRepository catalogs;
    private final ProductRepository products;

    public ProductCatalog requireCatalog(String storeId, String catalogId) {
        ProductCatalog catalog = catalogs.findById(storeId, catalogId);
        if (catalog == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return catalog;
    }

    public CategoryDefinition requireCategory(ProductCatalog catalog, String categoryId) {
        return catalog.getCategories().stream()
                .filter(c -> categoryId.equals(c.getCategoryId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Product requireProduct(CategoryDefinition category, String productId) {
        Product product = products.findByProductId(category.getCategoryId(), productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return product;
    }

    public String requireMarketplace(Store store, String name) {
        return store.getMarketplaces().stream()
                .map(MarketplaceIntegration::getName)
                .filter(name::equals)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
