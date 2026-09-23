package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductsBulkAddFormTest {

    private static Product product(String name, String label, String group) {
        return new Product("cat", "pim", "4719331361600", "m", "b", label, name, group);
    }

    private static Product withIdentifiers(String ean, String manufacturerCode) {
        return new Product("cat", "pim", ean, manufacturerCode, "b", "RTX 5070", "MSI RTX 5070", "Default");
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

    /**
     * The identifiers are editable here, so they are checked here, by the rules the product page uses: a product is
     * known by its EAN or by its manufacturer code, and an EAN that is given is 8--14 digits.
     */
    @Test
    void aRowNeedsAnIdentifierAndAnEanOfEightToFourteenDigits() {
        // given
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(
                withIdentifiers(null, null), withIdentifiers("12345", null), withIdentifiers(null, "MFN-1")));

        // when
        Map<String, String> errors = form.validate(List.of(), List.of("Default"));

        // then
        assertThat(errors).containsEntry("product-0-ean", "product.error.identifier.required")
                // Either field would fix a row with no identifier at all, so both are marked and both are linkable.
                .containsEntry("product-0-manufacturerCode", "product.error.identifier.required")
                .containsEntry("product-1-ean", "product.error.ean.invalid")
                .doesNotContainKey("product-1-manufacturerCode")
                .doesNotContainKey("product-2-ean");
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

    /**
     * One message repeats row after row ("Podaj nazwę produktu." three times), so the error summary names the row as
     * the operator counts them; the message at the field sits in its row and stays as it is. An error of the whole
     * form (nothing selected) belongs to no row.
     */
    @Test
    void theErrorSummaryNamesTheRowOfEveryErrorCountedFromOne() {
        // given
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("product-1-name", PolishMessages.text("product.error.name.required"));
        texts.put("product-11-ean", PolishMessages.text("product.error.identifier.required"));
        texts.put("products", PolishMessages.text("catalog.products.review.none"));

        // when
        Map<String, String> summary = ProductsBulkAddForm.summary(texts,
                (number, text) -> PolishMessages.text(ProductsBulkAddForm.SUMMARY_LINE, number, text));

        // then
        assertThat(summary).containsExactly(
                Map.entry("product-1-name", "Produkt 2: Podaj nazwę produktu."),
                Map.entry("product-11-ean", "Produkt 12: Podaj EAN albo kod producenta."),
                Map.entry("products", "Nie zaznaczono produktów."));
    }
}
