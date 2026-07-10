package pl.commercelink.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.StoreCategories;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutValidationTest {

    @Mock
    private StoreCategories storeCategories;

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
    @DisplayName("items from a service catalog definition are marked as services during checkout")
    void resolveServiceFlagsMarksItemsFromServiceDefinitions() {
        // given
        ProductCatalog catalog = catalogWithRequired("Obudowa", "Case");
        BasketItem serviceItem = item("Usługi dodatkowe");
        BasketItem productItem = item("Obudowa");
        when(storeCategories.serviceNames(List.of(catalog))).thenReturn(Set.of("Usługi dodatkowe"));

        // when
        checkout.resolveServiceFlags(catalog, List.of(serviceItem, productItem));

        // then
        assertThat(serviceItem.isService()).isTrue();
        assertThat(productItem.isService()).isFalse();
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
