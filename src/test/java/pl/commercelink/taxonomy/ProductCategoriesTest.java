package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoriesTest {

    @Test
    void parsesKnownCategoryKey() {
        // when / then
        assertThat(ProductCategories.tryParse("CPU")).contains(ProductCategory.CPU);
    }

    @Test
    void returnsEmptyForUnknownCategoryKey() {
        // when / then
        assertThat(ProductCategories.tryParse("Smartwatches")).isEmpty();
    }

    @Test
    void returnsEmptyForNullCategoryKey() {
        // when / then
        assertThat(ProductCategories.tryParse(null)).isEmpty();
    }
}
