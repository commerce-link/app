package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductAvailabilityType;

import java.util.ArrayList;
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

    private static ProductsBulkAddForm.Row row(String ean, String manufacturerCode) {
        ProductsBulkAddForm.Row row = new ProductsBulkAddForm.Row();
        row.setName("MSI RTX 5070");
        row.setEan(ean);
        row.setManufacturerCode(manufacturerCode);
        row.setPricingGroup("Default");
        return row;
    }

    /**
     * RF-3: the review posts the identifiers as typed; the product is saved with them normalised the way the product
     * page saves them, or it would never match its PIM entry or the inventory again.
     */
    @Test
    void aRowIsSavedWithItsIdentifiersNormalised() {
        // when
        Product spaced = row(" 5900000000101 ", " gv-n5080 oc ").toProduct("cat");
        Product gtin14 = row("05900000000101", null).toProduct("cat");
        Product blank = row("  ", "MFN-1").toProduct("cat");

        // then
        assertThat(spaced.getEan()).isEqualTo("5900000000101");
        assertThat(spaced.getManufacturerCode()).isEqualTo("GV-N5080OC");
        assertThat(gtin14.getEan()).isEqualTo("5900000000101");
        assertThat(blank.getEan()).isNull();
    }

    /** D-M48: the way of pricing is posted as text; a value no product can have is an error of its field. */
    @Test
    void anUnknownAvailabilityIsAnErrorOfItsFieldAndAKnownOneIsApplied() {
        // given
        ProductsBulkAddForm.Row forged = row("5901234567890", null);
        forged.setAvailabilityType("Forged");
        ProductsBulkAddForm.Row fixed = row("5901234567891", null);
        fixed.setAvailabilityType("AlwaysAvailable");
        ProductsBulkAddForm form = new ProductsBulkAddForm();
        form.setProducts(new ArrayList<>(List.of(forged, fixed)));

        // when
        Map<String, String> errors = form.validate(List.of(), List.of("Default"));

        // then
        assertThat(errors).containsOnlyKeys("product-0-availabilityType")
                .containsEntry("product-0-availabilityType", "product.error.availability.invalid");
        assertThat(fixed.toProduct("cat").getAvailabilityType()).isEqualTo(ProductAvailabilityType.AlwaysAvailable);
        assertThat(row("5901234567892", null).toProduct("cat").getAvailabilityType())
                .isEqualTo(ProductAvailabilityType.BasedOnSupply);
    }

    /** RF-6: the review is given an id of its own when it is shown, posted back with the rows. */
    @Test
    void everyReviewIsGivenAnIdOfItsOwn() {
        // when
        ProductsBulkAddForm first = ProductsBulkAddForm.of(List.of(product("A", "RTX 5070", "Default")));
        ProductsBulkAddForm second = ProductsBulkAddForm.of(List.of(product("A", "RTX 5070", "Default")));

        // then
        assertThat(first.getReviewId()).isNotBlank().isNotEqualTo(second.getReviewId());
        assertThat(java.util.UUID.fromString(first.getReviewId()).toString()).isEqualTo(first.getReviewId());
    }

    /**
     * RF-6: the same review posted twice saves every row under the same id both times, so the second write of a row
     * is a conditional put that finds the first one; another review, row or identifier gives another id.
     */
    @Test
    void theSameRowOfTheSameReviewIsSavedUnderTheSameId() {
        // given
        ProductsBulkAddForm review = ProductsBulkAddForm.of(List.of(product("A", "RTX 5070", "Default"),
                withIdentifiers("5901234567890", "M-2")));
        ProductsBulkAddForm sentAgain = new ProductsBulkAddForm();
        sentAgain.setReviewId(review.getReviewId());
        sentAgain.setProducts(review.getProducts());
        ProductsBulkAddForm another = ProductsBulkAddForm.of(review.getProducts().stream().map(row -> row.toProduct("cat")).toList());

        // when
        String first = review.toProduct(0, "cat").getProductId();

        // then
        assertThat(sentAgain.toProduct(0, "cat").getProductId()).isEqualTo(first);
        assertThat(review.toProduct(1, "cat").getProductId()).isNotEqualTo(first);
        assertThat(another.toProduct(0, "cat").getProductId()).isNotEqualTo(first);
        assertThat(review.toProduct(0, "other-cat").getProductId()).isNotEqualTo(first);
    }

    /** A row whose EAN was corrected before the review was sent again is another product, not the one saved first. */
    @Test
    void aRowWithOtherIdentifiersIsSavedUnderAnotherId() {
        // given
        ProductsBulkAddForm review = ProductsBulkAddForm.of(List.of(withIdentifiers("5901234567890", "M-1")));
        String first = review.toProduct(0, "cat").getProductId();

        // when
        review.getProducts().get(0).setEan("5901234567906");

        // then
        assertThat(review.toProduct(0, "cat").getProductId()).isNotEqualTo(first);
    }

    /** A review posted without an id of the generator's shape (an old page, a forged value) saves as before: fresh ids. */
    @Test
    void aReviewWithoutAWellFormedIdGivesEveryRowAFreshId() {
        // given
        ProductsBulkAddForm review = ProductsBulkAddForm.of(List.of(product("A", "RTX 5070", "Default")));
        review.setReviewId("not-a-uuid");

        // when / then
        assertThat(review.toProduct(0, "cat").getProductId()).isNotEqualTo(review.toProduct(0, "cat").getProductId());
    }
}
