package pl.commercelink.offer;

import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.*;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;

import java.util.Optional;

public class ProductDetails {

    private final PimCatalog pimCatalog;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final InventoryView inventory;

    public ProductDetails(PimCatalog pimCatalog, ProductCatalogRepository productCatalogRepository, ProductRepository productRepository, InventoryView inventory) {
        this.pimCatalog = pimCatalog;
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
            return createProductDetailsFromRecommendation(categoryDefinition, pimId);
        } else {
            return createProductDetailsFromMappedProduct(categoryId, pimId);
        }
    }

    private ProductDetailsView createProductDetailsFromRecommendation(CategoryDefinition categoryDefinition, String pimId) {
        Optional<PimEntry> pimEntry = pimCatalog.findByPimId(pimId);
        if (pimEntry.isEmpty()) {
            return null;
        }

        InventoryKey inventoryKey = InventoryKey.fromPimEntry(pimEntry.get());
        MatchedInventory matchedInventory = inventory.findByInventoryKey(inventoryKey);

        ProductRecommendation recommendation = new ProductRecommendation(categoryDefinition, matchedInventory, pimEntry);
        return createProductDetailsView(recommendation.toProduct());
    }

    private ProductDetailsView createProductDetailsFromMappedProduct(String categoryId, String pimId) {
        return createProductDetailsView(productRepository.findByPimId(categoryId, pimId));
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
                product.getName(),
                product.getCategory().getProductGroup(),
                product.getCategory()
        );

        p.setRecommendation(product.getRecommendation());
        p.setCustomAttributesFilters(product.getCustomAttributesFilters());
        p.setCustomAttributes(product.getCustomAttributes());
        p.setQuickFilters(product.getQuickFilters());
        p.setMetadata(product.getMetadata());

        return p;
    }

}
