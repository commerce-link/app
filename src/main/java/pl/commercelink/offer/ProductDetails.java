package pl.commercelink.offer;

import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.*;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.information.StorePimAliases;

import java.util.Optional;

public class ProductDetails {

    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final InventoryView inventory;
    private final StorePimAliases aliases;

    public ProductDetails(ProductCatalogRepository productCatalogRepository, ProductRepository productRepository,
                          InventoryView inventory, StorePimAliases aliases) {
        this.aliases = aliases;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.inventory = inventory;
    }

    public ProductDetailsView generate(String storeId, String catalogId, String categoryId, String pimId) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        if (catalog == null) {
            return null;
        }

        CategoryDefinition categoryDefinition = catalog.findCategoryDefinition(categoryId);
        if (categoryDefinition == null) {
            return null;
        }

        if (categoryDefinition.hasType(CategoryDefinitionType.Dynamic)) {
            return createProductDetailsFromRecommendation(storeId, categoryDefinition, pimId);
        } else {
            return createProductDetailsFromMappedProduct(storeId, categoryId, pimId);
        }
    }

    private ProductDetailsView createProductDetailsFromRecommendation(String storeId, CategoryDefinition categoryDefinition, String pimId) {
        // A link to the global entry shows the store's clone of it, as the category itself does
        Optional<PimEntry> pimEntry = aliases.effectiveEntry(storeId, pimId);
        if (pimEntry.isEmpty()) {
            return null;
        }

        InventoryKey inventoryKey = InventoryKey.fromPimEntry(pimEntry.get());
        MatchedInventory matchedInventory = inventory.findByInventoryKey(inventoryKey);

        ProductRecommendation recommendation = new ProductRecommendation(categoryDefinition, matchedInventory, pimEntry);
        return createProductDetailsView(recommendation.toProduct());
    }

    private ProductDetailsView createProductDetailsFromMappedProduct(String storeId, String categoryId, String pimId) {
        // Moving onto the clone, or back to the source when the clone is deleted, rewrites the product's pimId, but a
        // link may still carry the other id (shared or cached before the move), so the clone and the source are tried
        Product product = productRepository.findByPimId(categoryId, pimId);
        if (product == null) {
            product = aliases.cloneOf(storeId, pimId).map(id -> productRepository.findByPimId(categoryId, id)).orElse(null);
        }
        if (product == null) {
            product = aliases.sourceOf(storeId, pimId).map(id -> productRepository.findByPimId(categoryId, id)).orElse(null);
        }
        return createProductDetailsView(product);
    }

    private ProductDetailsView createProductDetailsView(pl.commercelink.products.Product product) {
        if (product == null) {
            return null;
        }

        ProductDetailsView p = new ProductDetailsView(
                product.getCategoryId(),
                product.getPimId(),
                product.getManufacturerCode(),
                product.getBrand(),
                product.getLabel(),
                product.getName()
        );

        p.setRecommendation(product.getRecommendation());
        p.setCustomAttributesFilters(product.getCustomAttributesFilters());
        p.setCustomAttributes(product.getCustomAttributes());
        p.setQuickFilters(product.getQuickFilters());
        p.setMetadata(product.getMetadata());

        return p;
    }

}
