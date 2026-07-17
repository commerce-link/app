package pl.commercelink.pricelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductAvailabilityType;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityAndPriceListTest {

    @Mock
    private InventoryView inventory;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductRecommendationEngine recommendationEngine;

    @Mock
    private RollingPriceAggregateRepository rollingPriceAggregateRepository;

    @Mock
    private SellingPriceHistoryRepository sellingPriceHistoryRepository;

    @Mock
    private ProductCatalog catalog;

    @Mock
    private CategoryDefinition categoryDefinition;

    @Mock
    private MatchedInventory matchedInventory;

    @Mock
    private ProductRecommendation recommendation;

    private AvailabilityAndPriceList availabilityAndPriceList;
    private Product product;

    @BeforeEach
    void setUp() {
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Collections.emptyMap());
        availabilityAndPriceList = new AvailabilityAndPriceList(
                inventory,
                productCatalogRepository,
                productRepository,
                recommendationEngine,
                rollingPriceAggregateRepository,
                sellingPriceHistoryRepository
        );

        product = new Product();
        product.setPimId("pim-1");
        product.setEan("5900000000001");
        product.setManufacturerCode("FD-C-NOR1C-01");
        product.setBrand("Fractal Design");
        product.setLabel("label");
        product.setName("Fractal Design North");
        product.setAvailabilityType(ProductAvailabilityType.AlwaysAvailable);
        product.setSuggestedRetailPrice(1000);
        product.setEstimatedDeliveryDays(3);

        mockCatalogWithSingleDefinition();
    }

    @Test
    void managedCatalogRowTakesCategoryFromDefinitionNameNotFromProduct() {
        // given
        when(categoryDefinition.hasType(CategoryDefinitionType.Dynamic)).thenReturn(false);
        when(categoryDefinition.getCategoryId()).thenReturn("cat-1");
        when(productRepository.findAllProductsWithPimId("cat-1", true)).thenReturn(List.of(product));

        // when
        List<AvailabilityAndPrice> result = availabilityAndPriceList.generate("store-1", "catalog-1");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Obudowa");
    }

    @Test
    void dynamicCatalogRowTakesCategoryFromDefinitionNameNotFromProduct() {
        // given
        when(categoryDefinition.hasType(CategoryDefinitionType.Dynamic)).thenReturn(true);
        when(recommendationEngine.getRecommendationsForMappedProducts(categoryDefinition, inventory)).thenReturn(List.of(recommendation));
        when(recommendation.toProduct()).thenReturn(product);

        // when
        List<AvailabilityAndPrice> result = availabilityAndPriceList.generate("store-1", "catalog-1");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Obudowa");
    }

    private void mockCatalogWithSingleDefinition() {
        when(sellingPriceHistoryRepository.load("store-1", "catalog-1")).thenReturn(Map.of());
        when(productCatalogRepository.findById("store-1", "catalog-1")).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(categoryDefinition));
        when(categoryDefinition.getName()).thenReturn("Obudowa");
        when(categoryDefinition.hasGrouping()).thenReturn(false);
        when(inventory.findByProduct(any())).thenReturn(matchedInventory);
        when(matchedInventory.getTotalAvailableQty(1000)).thenReturn(5L);
    }
}
