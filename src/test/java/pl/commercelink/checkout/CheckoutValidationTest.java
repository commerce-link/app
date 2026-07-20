package pl.commercelink.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CheckoutValidationTest {

    @InjectMocks
    private Checkout checkout;

    @Test
    @DisplayName("required category passes when the item carries the definition name from a new pricelist")
    void passesWhenItemCarriesDefinitionName() {
        // given
        ProductCatalog catalog = catalogWithRequired("Obudowa", "Case");

        // when / then
        assertThatCode(() -> checkout.validateOrderCompleteness(catalog, List.of(item("Obudowa"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("required category passes when the item still carries the legacy category key from an old pricelist")
    void passesWhenItemCarriesLegacyCategoryKey() {
        // given
        ProductCatalog catalog = catalogWithRequired("Obudowa", "Case");

        // when / then
        assertThatCode(() -> checkout.validateOrderCompleteness(catalog, List.of(item("Case"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("required category throws when no item matches the definition")
    void throwsWhenRequiredCategoryIsMissing() {
        // given
        ProductCatalog catalog = catalogWithRequired("Obudowa", "Case");

        // when / then
        assertThatThrownBy(() -> checkout.validateOrderCompleteness(catalog, List.of(item("Procesor"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Obudowa");
    }

    @Test
    @DisplayName("item without a category never satisfies a required definition without a category mapping")
    void itemWithNullCategoryDoesNotSatisfyRequiredDefinitionWithoutCategoryMapping() {
        // given
        CategoryDefinition unmappedDefinition = new CategoryDefinition();
        unmappedDefinition.setName("Montaż");
        unmappedDefinition.setRequiredDuringOrder(true);
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(unmappedDefinition));

        // when / then
        assertThatThrownBy(() -> checkout.validateOrderCompleteness(catalog, List.of(item(null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Montaż");
    }

    private ProductCatalog catalogWithRequired(String name, String category) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setName(name);
        definition.setCategory(category);
        definition.setRequiredDuringOrder(true);
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(definition));
        return catalog;
    }

    private BasketItem item(String category) {
        return new BasketItem("pim-1", "name", "mfn", category, 100.0, 0, 1, null, 1, false);
    }
}
