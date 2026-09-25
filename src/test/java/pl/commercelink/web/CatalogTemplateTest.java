package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.web.catalog.CategoryRow;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/catalog.html"), StandardCharsets.UTF_8);
    }

    @Test
    void listsTheCategoriesOfTheCatalogAsACardList() throws Exception {
        // when / then
        assertThat(page()).contains("settings-header :: subpageWithActions").contains("class=\"cl-list\"")
                .contains("th:each=\"category : ${categories}\"").contains("${category.href()}")
                .contains("${category.settingsHref()}").contains("cl-list-empty")
                .contains("#{catalog.category.add}");
    }

    @Test
    void usesNoBulmaComponentsInlineStylesOrTables() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("class=\"button is-").doesNotContain("style=").doesNotContain("<table")
                .doesNotContain("notification is-").doesNotContain("dropdown");
    }

    @Test
    void showsTheOutcomeOfACatalogActionInThePageBodyOnly() throws Exception {
        // when / then
        assertThat(page()).contains("settings-form :: savedAlert").contains("th:if=\"${catalogError}\"")
                .contains("class=\"cl-alert is-bad\" role=\"status\"")
                .doesNotContain("errorMessage").doesNotContain("warningMessage");
    }

    @Test
    void deletingACategoryGoesThroughTheSharedConfirmation() throws Exception {
        // when / then
        assertThat(page()).contains("data-cl-confirm").contains("fragments/confirm-dialog :: dialog")
                .contains("@{/js/confirm-dialog.js}").contains("th:if=\"${category.deletable()}\"")
                .doesNotContain("confirmDelete(");
    }

    /**
     * The page as the controller renders it: a manual category whose products the deletion takes with it, an automatic
     * one protected from deletion, a manual one whose products another category keeps alive, and an automatic one that
     * can be deleted.
     */
    private static String rendered() {
        ProductCatalog catalog = new ProductCatalog("store-1", "Parts");
        CategoryRow gpu = new CategoryRow("k1", "GPU", false, List.of("Graphics cards"), 245, false, 245, 10, 1,
                List.of("Allegro", "Empik"), 1, true, true, "/dashboard/catalogs/c1/category/k1",
                "/dashboard/catalogs/c1/category/k1/settings", "/dashboard/catalogs/c1/category/k1/delete");
        CategoryRow os = new CategoryRow("k2", "OS", true, List.of(), null, false, 0, 0, 0, List.of(), 1, false, false,
                "/dashboard/catalogs/c1/category/k2", "/dashboard/catalogs/c1/category/k2/settings",
                "/dashboard/catalogs/c1/category/k2/delete");
        CategoryRow cpu = new CategoryRow("k3", "CPU", false, List.of("Processors"), 12, true, 0, 0, 0, List.of(), 1,
                false, true, "/dashboard/catalogs/c1/category/k3", "/dashboard/catalogs/c1/category/k3/settings",
                "/dashboard/catalogs/c1/category/k3/delete");
        CategoryRow drivers = new CategoryRow("k4", "Drivers", true, List.of("Drivers"), null, false, 0, 0, 0,
                List.of(), 1, false, true, "/dashboard/catalogs/c1/category/k4",
                "/dashboard/catalogs/c1/category/k4/settings", "/dashboard/catalogs/c1/category/k4/delete");
        Context context = new Context();
        context.setVariable("catalog", catalog);
        context.setVariable("categories", List.of(gpu, os, cpu, drivers));
        context.setVariable("productsTotal", 245);
        context.setVariable("scheduleText", "every 30 minutes");
        context.setVariable("settingsHref", "/dashboard/catalogs/c1/settings");
        context.setVariable("addCategoryHref", "/dashboard/catalogs/c1/category/new");
        context.setVariable("settingsSavedMessage", null);
        context.setVariable("catalogError", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/catalog", context);
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void theHeaderActionsAreRenderedOnceAndEveryMessageResolves() {
        // when
        String html = rendered();

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(occurrences(html, "/dashboard/catalogs/c1/category/new")).isEqualTo(1);
        assertThat(html.indexOf("cl-page-actions")).isLessThan(html.indexOf("/dashboard/catalogs/c1/category/new"));
        assertThat(html).doesNotContain("??");
    }

    /**
     * Products outlive a category whose PIM categories another category of the catalog uses too; the dialog says so
     * instead of promising a deletion the confirmation page behind it would contradict.
     */
    @Test
    void theDeleteDialogSaysWhetherTheProductsSurviveTheCategory() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("The products of this category will not be deleted, but will disappear from the panel and the price list.")
                .contains("This removes 245 products of this category.")
                .contains("The list of this category is worked out from the inventory");
    }

    @Test
    void onlyAnUnprotectedCategoryOffersDeletion() {
        // when
        String html = rendered();

        // then
        assertThat(occurrences(html, "/dashboard/catalogs/c1/category/k1/delete")).isEqualTo(1);
        assertThat(html).doesNotContain("/dashboard/catalogs/c1/category/k2/delete");
        assertThat(html).contains("Automatic").contains("Products from the inventory")
                .contains("Products with a subcategory outside the list: 1")
                .contains("Marketplaces: Allegro, Empik");
    }
}
