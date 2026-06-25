package pl.commercelink.checkout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.taxonomy.ProductCategory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class GoldenCheckoutRequiredComponentTest {

    private static final String REQUIRED_ITEM_MISSING_MESSAGE = "Required item missing: ";

    private final Checkout checkout = new Checkout();

    @Test
    void itemMatchingRequiredCategoryByEnumPassesValidation() {
        // given
        ProductCatalog catalog = catalogWithRequired(ProductCategory.CPU);
        List<BasketItem> items = List.of(item(ProductCategory.CPU, 1));

        // when / then
        assertThatCode(() -> validateOrderCompleteness(catalog, items))
                .doesNotThrowAnyException();
    }

    @Test
    void itemWithNonMatchingCategoryThrowsRequiredItemMissing() {
        // given
        ProductCatalog catalog = catalogWithRequired(ProductCategory.CPU);
        List<BasketItem> items = List.of(item(ProductCategory.GPU, 1));

        // when / then
        assertThatThrownBy(() -> validateOrderCompleteness(catalog, items))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REQUIRED_ITEM_MISSING_MESSAGE + ProductCategory.CPU);
    }

    @Test
    void emptyBasketAgainstRequiredCategoryThrowsRequiredItemMissing() {
        // given
        ProductCatalog catalog = catalogWithRequired(ProductCategory.CPU);
        List<BasketItem> items = List.of();

        // when / then
        assertThatThrownBy(() -> validateOrderCompleteness(catalog, items))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REQUIRED_ITEM_MISSING_MESSAGE + ProductCategory.CPU);
    }

    @Test
    void matchingCategoryWithZeroQtyThrowsRequiredItemMissing() {
        // given
        ProductCatalog catalog = catalogWithRequired(ProductCategory.CPU);
        List<BasketItem> items = List.of(item(ProductCategory.CPU, 0));

        // when / then
        assertThatThrownBy(() -> validateOrderCompleteness(catalog, items))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REQUIRED_ITEM_MISSING_MESSAGE + ProductCategory.CPU);
    }

    @Test
    void nonRequiredCategoryIsNotValidated() {
        // given
        CategoryDefinition optional = new CategoryDefinition();
        optional.setCategory(ProductCategory.CPU);
        optional.setRequiredDuringOrder(false);
        ProductCatalog catalog = new ProductCatalog();
        catalog.getCategories().add(optional);
        List<BasketItem> items = List.of();

        // when / then
        assertThatCode(() -> validateOrderCompleteness(catalog, items))
                .doesNotThrowAnyException();
    }

    @Test
    void multipleRequiredCategoriesReportFirstMissingByMessage() {
        // given
        ProductCatalog catalog = new ProductCatalog();
        catalog.getCategories().add(required(ProductCategory.CPU));
        catalog.getCategories().add(required(ProductCategory.GPU));
        List<BasketItem> items = List.of(item(ProductCategory.CPU, 1));

        // when / then
        assertThatThrownBy(() -> validateOrderCompleteness(catalog, items))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REQUIRED_ITEM_MISSING_MESSAGE + ProductCategory.GPU);
    }

    @Test
    void messageContainsEnumNameNotCategoryDefinitionName() {
        // given
        CategoryDefinition required = new CategoryDefinition();
        required.setName("Procesory");
        required.setCategory(ProductCategory.CPU);
        required.setRequiredDuringOrder(true);
        ProductCatalog catalog = new ProductCatalog();
        catalog.getCategories().add(required);
        List<BasketItem> items = List.of(item(ProductCategory.GPU, 1));

        // when
        Throwable thrown = catchValidation(catalog, items);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage()).isEqualTo(REQUIRED_ITEM_MISSING_MESSAGE + ProductCategory.CPU);
        assertThat(thrown.getMessage()).doesNotContain("Procesory");
    }

    private ProductCatalog catalogWithRequired(ProductCategory category) {
        ProductCatalog catalog = new ProductCatalog();
        catalog.getCategories().add(required(category));
        return catalog;
    }

    private CategoryDefinition required(ProductCategory category) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategory(category);
        definition.setRequiredDuringOrder(true);
        return definition;
    }

    private BasketItem item(ProductCategory category, long qty) {
        BasketItem item = new BasketItem();
        item.setCategoryKey(category != null ? category.name() : null);
        item.setQty(qty);
        return item;
    }

    private Throwable catchValidation(ProductCatalog catalog, List<BasketItem> items) {
        try {
            validateOrderCompleteness(catalog, items);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private void validateOrderCompleteness(ProductCatalog catalog, List<BasketItem> items) throws Throwable {
        try {
            Method method = Checkout.class.getDeclaredMethod(
                    "validateOrderCompleteness", ProductCatalog.class, List.class);
            method.setAccessible(true);
            method.invoke(checkout, catalog, items);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
