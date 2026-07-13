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
import pl.commercelink.starter.localization.EnumLocalizer;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.taxonomy.ProductGroup;

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
    private EnumLocalizer enumLocalizer;

    @Mock
    private CategoryLocalizer categoryLocalizer;

    @Mock
    private ProductCatalog catalog;

    @InjectMocks
    private ProductCatalogRestApi restApi;

    @Test
    void categoryTreeLocalizesLeafThroughTheInventoryCategorySoOtherLanguagesStillResolve() {
        // given
        catalogWithSingleDefinition("Karty graficzne");
        when(categoryLocalizer.localize("GPU", "plural")).thenReturn("Graphics cards");
        when(enumLocalizer.localize(ProductGroup.PcComponents)).thenReturn("PC components");

        // when
        List<ProductCategoryTree> tree = categoriesTree();

        // then
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getCategoryTree()).isEqualTo("PC components/Graphics cards");
    }

    @Test
    void categoryTreeResolvesProductGroupThroughIcecatBridgeForLeafCategory() {
        // given
        catalogWithSingleDefinition("Karty graficzne");
        when(categoryLocalizer.localize("GPU", "plural")).thenReturn("Karty graficzne");
        when(enumLocalizer.localize(ProductGroup.PcComponents)).thenReturn("Podzespoły komputerowe");

        // when
        List<ProductCategoryTree> tree = categoriesTree();

        // then
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getCategoryTree()).isEqualTo("Podzespoły komputerowe/Karty graficzne");
    }

    @Test
    void categoryTreeHasNoProductGroupPrefixForCategoryOutsideTheBridge() {
        // given
        catalogWithSingleDefinition("Kołdry");
        when(categoryLocalizer.localize("Kołdry", "plural")).thenReturn("Kołdry");

        // when
        List<ProductCategoryTree> tree = categoriesTree();

        // then
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getCategoryTree()).isEqualTo("Kołdry");
    }

    @Test
    void marksDefinitionsAsDuplicatedWhenTheirCategoriesResolveToTheSameInventoryCategory() {
        // given
        catalogWithDefinitions(
                definition("category-1", "Karty RTX", "GPU"),
                definition("category-2", "Karty GTX", "Karty graficzne"));
        when(categoryLocalizer.localize("GPU", "plural")).thenReturn("Karty graficzne");
        when(enumLocalizer.localize(ProductGroup.PcComponents)).thenReturn("Podzespoły komputerowe");

        // when
        List<ProductCategoryTree> tree = categoriesTree();

        // then
        assertThat(tree).extracting(ProductCategoryTree::getCategoryTree)
                .containsExactly(
                        "Podzespoły komputerowe/Karty graficzne/Karty RTX",
                        "Podzespoły komputerowe/Karty graficzne/Karty GTX");
    }

    private void catalogWithSingleDefinition(String category) {
        catalogWithDefinitions(definition("category-1", "Karta graficzna", category));
    }

    private void catalogWithDefinitions(CategoryDefinition... definitions) {
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
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
