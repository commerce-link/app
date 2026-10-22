package pl.commercelink.offer;

import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.*;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProductsList {

    private final PimCatalog pimCatalog;
    private final ProductRecommendationEngine recommendationEngine;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final InventoryView inventory;

    public ProductsList(PimCatalog pimCatalog, ProductRecommendationEngine recommendationEngine, ProductCatalogRepository productCatalogRepository, ProductRepository productRepository, InventoryView inventory) {
        this.pimCatalog = pimCatalog;
        this.recommendationEngine = recommendationEngine;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.inventory = inventory;
    }

    public List<ProductView> generate(String storeId, String catalogId, String categoryId) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        if (catalog == null) {
            return new LinkedList<>();
        }

        return generate(
                storeId,
                catalog.getCategories().stream().filter(c -> c.getCategoryId().equals(categoryId)).collect(Collectors.toList())
        );
    }

    private List<ProductView> generate(String storeId, List<CategoryDefinition> categories) {
        return categories.stream().flatMap(c -> {
            if (c.hasType(CategoryDefinitionType.Dynamic)) {
                return computeProductsListBasedOnRecommendations(storeId, c);
            }
            return fetchProductsFromDb(c);

        }).collect(Collectors.toList());
    }

    private Stream<ProductView> computeProductsListBasedOnRecommendations(String storeId, CategoryDefinition c) {
        List<ProductRecommendation> recommendations = recommendationEngine.getRecommendationsForMappedProducts(pimCatalog.withStoreData(storeId), c, inventory);
        return recommendations.stream()
                .map(ProductRecommendation::toProduct)
                .map(this::createProductInfo);
    }

    private Stream<ProductView> fetchProductsFromDb(CategoryDefinition c) {
        return productRepository.findAllProductsWithPimId(c.getCategoryId(), true)
                .stream()
                .map(this::createProductInfo);
    }

    private ProductView createProductInfo(pl.commercelink.products.Product product) {
        return new ProductView(
                product.getCategoryId(),
                product.getPimId(),
                product.getManufacturerCode(),
                product.getBrand(),
                product.getLabel(),
                product.getName()
        );
    }

}
