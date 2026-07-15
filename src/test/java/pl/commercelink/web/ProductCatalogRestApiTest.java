package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.offer.ProductCategoryTree;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pricelist.AvailabilityAndPriceListFactory;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogRestApiTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private Inventory inventory;

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductRecommendationEngine recommendationEngine;

    @Mock
    private PricelistRepository pricelistRepository;

    @Mock
    private AvailabilityAndPriceListFactory availabilityAndPriceListFactory;

    @Mock
    private ProductCatalog catalog;

    @InjectMocks
    private ProductCatalogRestApi restApi;

    @Test
    void categoryTreeBuildsPathFromCatalogAndDefinitionNames() {
        // given
        catalogWithDefinitions("Elektronika",
                definition("category-1", "Karty RTX", "Karty graficzne"),
                definition("category-2", "Procesory do gier", "Procesory"));

        // when
        List<ProductCategoryTree> tree = categoriesTree();

        // then
        assertThat(tree).extracting(ProductCategoryTree::getPath)
                .containsExactly("Elektronika/Karty RTX", "Elektronika/Procesory do gier");
        assertThat(tree).extracting(ProductCategoryTree::getCategoryId)
                .containsExactly("category-1", "category-2");
    }

    private void catalogWithDefinitions(String catalogName, CategoryDefinition... definitions) {
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getName()).thenReturn(catalogName);
        when(catalog.getCategories()).thenReturn(List.of(definitions));
    }

    private CategoryDefinition definition(String categoryId, String name, String category) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId(categoryId);
        definition.setName(name);
        definition.setCategory(category);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private List<ProductCategoryTree> categoriesTree() {
        return (List<ProductCategoryTree>) ReflectionTestUtils.invokeMethod(restApi, "getCategoriesTree", STORE_ID, CATALOG_ID);
    }
}
