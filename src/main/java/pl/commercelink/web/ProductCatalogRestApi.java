package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.offer.*;
import pl.commercelink.pricelist.AvailabilityAndPriceListFactory;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.web.dtos.ObjectIdDto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/Store/{storeId}/Catalog/{catalogId}")
public class ProductCatalogRestApi {

    @Autowired
    private Inventory inventory;

    @Autowired
    private PimCatalog pimCatalog;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRecommendationEngine recommendationEngine;

    @Autowired
    private PricelistRepository pricelistRepository;

    @Autowired
    private AvailabilityAndPriceListFactory availabilityAndPriceListFactory;

    @GetMapping("/PricelistId")
    public ObjectIdDto getNewestPricelistId(@PathVariable("storeId") String storeId,
                                            @PathVariable("catalogId") String catalogId) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        if (Objects.isNull(catalog)) {
            throw new RuntimeException("Internal error, contact administrator for more details.");
        }
        return new ObjectIdDto(pricelistRepository.findNewestPricelistId(catalogId));
    }

    @GetMapping("/Pricelist")
    public byte[] getNewestPricelist(@PathVariable("storeId") String storeId,
                                     @PathVariable("catalogId") String catalogId) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        if (Objects.isNull(catalog)) {
            throw new RuntimeException("Internal error, contact administrator for more details.");
        }
        return pricelistRepository.findNewestPricelistAsBytes(catalogId);
    }

    @GetMapping("/Categories")
    @ResponseBody
    private List<ProductCategoryTree> getCategoriesTree(@PathVariable("storeId") String storeId, @PathVariable("catalogId") String catalogId) {
        List<CategoryDefinition> categoryDefinitions = productCatalogRepository.findById(storeId, catalogId).getCategories();
        return categoryDefinitions.stream()
                .map(c -> new ProductCategoryTree(c, categoryDefinitions.stream().anyMatch(d -> d != c && d.getCategory() == c.getCategory())))
                .collect(Collectors.toList());
    }

    @GetMapping("/Categories/{categoryId}/Products")
    @ResponseBody
    private List<ProductView> getProducts(
            @PathVariable("storeId") String storeId,
            @PathVariable("catalogId") String catalogId,
            @PathVariable("categoryId") String categoryId) {
        return new ProductsList(recommendationEngine, productCatalogRepository, productRepository, inventory.withEnabledSuppliersAndWarehouseData(storeId))
                .generate(storeId, catalogId, categoryId);
    }

    @GetMapping("/Categories/{categoryId}/Products/{productId}")
    @ResponseBody
    private ProductDetailsView getProducts(
            @PathVariable("storeId") String storeId,
            @PathVariable("catalogId") String catalogId,
            @PathVariable("categoryId") String categoryId,
            @PathVariable("productId") String productId) {
        return new ProductDetails(pimCatalog, productCatalogRepository, productRepository, inventory.withEnabledSuppliersAndWarehouseData(storeId))
                .generate(storeId, catalogId, categoryId, productId);
    }

}