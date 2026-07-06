package pl.commercelink.checkout;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutTest {

    private final Checkout checkout = new Checkout();

    @Test
    void acceptsItemsWithUnknownCategoryWhenRequiredCategoriesArePresent() {
        // given
        ProductCatalog catalog = catalogRequiring(ProductCategory.CPU);
        List<BasketItem> items = List.of(
                basketItem("Smartwatches"),
                basketItem("CPU"));

        // when / then
        assertThatCode(() -> checkout.validateOrderCompleteness(catalog, items))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsItemsMissingRequiredCategory() {
        // given
        ProductCatalog catalog = catalogRequiring(ProductCategory.CPU);
        List<BasketItem> items = List.of(basketItem("Smartwatches"));

        // when / then
        assertThatThrownBy(() -> checkout.validateOrderCompleteness(catalog, items))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Required item missing");
    }

    private ProductCatalog catalogRequiring(ProductCategory category) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategory(category);
        definition.setRequiredDuringOrder(true);
        ProductCatalog catalog = new ProductCatalog();
        catalog.setCategories(List.of(definition));
        return catalog;
    }

    private BasketItem basketItem(String category) {
        return new BasketItem("id-" + category, "name", "mfn", category, 100.0, 80.0, 1, null, 1, false);
    }
}
