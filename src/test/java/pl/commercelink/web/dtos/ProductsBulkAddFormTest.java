package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductsBulkAddFormTest {

    private static Product product(String name, String label, String group) {
        return new Product("cat", "pim", "1", "m", "b", label, name, group);
    }

    @Test
    void namesAreRequiredAndLabelsMustComeFromTheListWhenTheCategoryHasOne() {
        // given
        ProductsBulkAddForm form = ProductsBulkAddForm.of(
                List.of(product("", "RTX 5070", "Default"), product("X", "RTX 4060", "Nope")));

        // when
        Map<String, String> errors = form.validate(List.of("RTX 5060", "RTX 5070"), List.of("Default", "Premium"));

        // then
        assertThat(errors).containsEntry("product-0-name", "product.error.name.required")
                .containsEntry("product-1-label", "product.error.label.notInList")
                .containsEntry("product-1-pricingGroup", "product.error.group.unknown");
    }

    /** The key of an error is the id of the field it belongs to, so the summary can link to it. */
    @Test
    void everyErrorIsKeyedByTheIdOfItsField() {
        // when / then
        assertThat(ProductsBulkAddForm.fieldId(2, "name")).isEqualTo("product-2-name");
    }

    @Test
    void withoutCategoryLabelsAnyLabelPassesAndAnEmptyListIsAnError() {
        // when / then
        assertThat(ProductsBulkAddForm.of(List.of(product("X", "anything", "Default")))
                .validate(List.of(), List.of("Default"))).isEmpty();
        assertThat(ProductsBulkAddForm.of(List.of()).validate(List.of(), List.of("Default")))
                .containsEntry("products", "catalog.products.review.none");
    }
}
