package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.warehouse.builtin.RestockForm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseRestockRenderingTest {

    @Test
    void priceLimitIsOptionalAndCategoriesFollowTheCatalog() {
        // given
        ProductCatalog catalog = new ProductCatalog();
        catalog.setCatalogId("c1");
        catalog.setName("Komputery");
        Context context = new Context();
        context.setVariable("restock", new RestockForm(List.of(catalog),
                Map.of("c1", List.of(Map.of("id", "k1", "name", "GPU"))), "c1", null));

        // when
        String html = EnglishFragmentTemplateEngine.create()
                .process("<form th:insert=\"~{fragments/warehouse-restock :: fields}\"></form>", context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).containsPattern("<select[^>]*name=\"restockPrice\"(?![^>]*required)[^>]*>");
        assertThat(html).contains("<option value=\"\">No limit</option>");
        assertThat(html).contains("data-cl-options-for=\"restock-catalog\"").contains("data-parent=\"c1\"");
        assertThat(html).containsPattern("<option[^>]*value=\"c1\"[^>]*selected");
    }

    private static Context context(String error) {
        ProductCatalog catalog = new ProductCatalog();
        catalog.setCatalogId("c1");
        catalog.setName("Komputery");
        catalog.setCategories(List.of());
        Context context = new Context();
        context.setVariable("restock", new RestockForm(List.of(catalog), Map.of(), null, error));
        return context;
    }

    @Test
    void aMissingCatalogIsSummarisedWithALinkToTheSelectWhichItDescribes() {
        // when
        String html = EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{warehouse-restock :: body}\"></div>", context("Choose a catalog."));

        // then
        assertThat(html).contains("data-cl-error-summary").contains("href=\"#restock-catalog\"")
                .containsPattern("<select[^>]*id=\"restock-catalog\"[^>]*aria-describedby=\"restock-catalog-error\"")
                .containsPattern("<select[^>]*id=\"restock-catalog\"[^>]*aria-invalid=\"true\"")
                .contains("id=\"restock-catalog-error\"").doesNotContain("role=\"alert\"");
    }

    @Test
    void withoutAnErrorTheCatalogSelectIsNeitherInvalidNorDescribed() {
        // when
        String html = EnglishFragmentTemplateEngine.create()
                .process("<form th:insert=\"~{fragments/warehouse-restock :: fields}\"></form>", context(null));

        // then
        assertThat(html).doesNotContain("data-cl-error-summary").doesNotContain("restock-catalog-error")
                .doesNotContain("aria-invalid");
    }

    @Test
    void theScopeAndTheStockCheckboxDoNotUseMissingInTwoMeanings() {
        // when
        String html = EnglishFragmentTemplateEngine.create()
                .process("<form th:insert=\"~{fragments/warehouse-restock :: fields}\"></form>", context(null));

        // then
        assertThat(html).contains("We order the quantity needed to reach the stock set in the catalog.")
                .contains("Only products that are not in stock").contains("Skips products already in stock.")
                .doesNotContainPattern(">[^<]*(?i:missing)[^<]*<");
    }

    @Test
    void thePageIsANarrowFormWithAHeaderElementAndFocusesItsErrors() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/warehouse-restock.html"));

        // then
        assertThat(template).contains("cl-page-body is-form").doesNotContain("is-wide")
                .contains("<header class=\"cl-page-header\">").contains("/js/error-summary.js");
    }

    @Test
    void aCheckboxAfterAFieldInAGroupIsAsFarFromItAsTheNextField() throws Exception {
        // when
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains(".cl-page .cl-fieldset > .cl-field + .cl-check-field");
    }
}
