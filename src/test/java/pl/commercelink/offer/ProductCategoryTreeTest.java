package pl.commercelink.offer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.taxonomy.ProductGroup;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoryTreeTest {

    @Test
    @DisplayName("resolves product group from a category key known to the legacy enum")
    void resolvesProductGroupFromKnownCategoryKey() {
        // given
        CategoryDefinition definition = definition("CPU", "Gaming CPUs");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, false, "Procesory", "Podzespoly");

        // then
        assertThat(tree.getProductCategory()).isEqualTo("CPU");
        assertThat(tree.getProductGroup()).isEqualTo(ProductGroup.PcComponents);
        assertThat(tree.getCategoryTree()).isEqualTo("Podzespoly/Procesory");
    }

    @Test
    @DisplayName("keeps unknown category key without product group instead of failing")
    void keepsUnknownCategoryKeyWithoutProductGroup() {
        // given
        CategoryDefinition definition = definition("IcecatFreeCategory", "Custom");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, false, "IcecatFreeCategory", null);

        // then
        assertThat(tree.getProductCategory()).isEqualTo("IcecatFreeCategory");
        assertThat(tree.getProductGroup()).isNull();
        assertThat(tree.getCategoryTree()).isEqualTo("IcecatFreeCategory");
    }

    @Test
    @DisplayName("resolves product group for an IceCat leaf name that maps onto the legacy enum")
    void resolvesProductGroupFromIcecatLeafName() {
        // given
        CategoryDefinition definition = definition("Procesory", "Gaming CPUs");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, false, "Procesory", "Podzespoly");

        // then
        assertThat(tree.getProductCategory()).isEqualTo("Procesory");
        assertThat(tree.getProductGroup()).isEqualTo(ProductGroup.PcComponents);
        assertThat(tree.getCategoryTree()).isEqualTo("Podzespoly/Procesory");
    }

    @Test
    @DisplayName("leaves product group empty for an IceCat leaf with no legacy enum counterpart")
    void leavesProductGroupEmptyForIcecatOnlyLeaf() {
        // given
        CategoryDefinition definition = definition("Kołdry", "Pościel");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, false, "Kołdry", null);

        // then
        assertThat(tree.getProductGroup()).isNull();
        assertThat(tree.getCategoryTree()).isEqualTo("Kołdry");
    }

    @Test
    @DisplayName("appends definition name to the tree path when category key is duplicated in the catalog")
    void appendsDefinitionNameWhenCategoryKeyDuplicated() {
        // given
        CategoryDefinition definition = definition("CPU", "Office CPUs");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, true, "Procesory", "Podzespoly");

        // then
        assertThat(tree.getCategoryTree()).isEqualTo("Podzespoly/Procesory/Office CPUs");
    }

    private CategoryDefinition definition(String categoryKey, String name) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setName(name);
        definition.setCategory(categoryKey);
        return definition;
    }
}
