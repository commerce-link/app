package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.web.inventory.BrowsePage;
import pl.commercelink.web.inventory.BrowseQuery;

import static org.assertj.core.api.Assertions.assertThat;

/** The two inventory pages rendered whole (without the layout around them): what each carries and which scripts it loads. */
class InventoryPageTemplateTest {

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void searchFormWorksWithoutJavaScriptAsAPlainGet() {
        // when
        String html = prices(false, null);

        // then
        assertThat(html).contains("<form class=\"cl-inv-search\" action=\"/dashboard/inventory/prices\" method=\"get\" role=\"search\"")
                .contains("name=\"q\"").doesNotContain("disabled");
    }

    @Test
    void newSearchWithoutJavaScriptKeepsTheWayBackToTheBrowseList() {
        // given
        Context context = new Context();
        context.setVariable("query", "5900000000126");
        context.setVariable("backToBrowse", "/dashboard/inventory?cat=11&page=2");
        String form = "<form th:replace=\"~{inventory-prices :: form[data-inventory-search]}\"></form>";

        // when
        String opened = engine.process(form, context);
        context.removeVariable("backToBrowse");
        String direct = engine.process(form, context);

        // then
        assertThat(opened).contains("<input type=\"hidden\" name=\"from\" value=\"/dashboard/inventory?cat=11&amp;page=2\">");
        assertThat(direct).contains("name=\"q\"").doesNotContain("name=\"from\"");
    }

    @Test
    void priceComparisonExposesTheHooksTheScriptSwapsFragmentsInto() {
        // when
        String html = prices(false, null);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-inventory-page", "data-page-url=\"/dashboard/inventory/prices\"",
                "<div id=\"inventory-results\">", "aria-live=\"polite\"", "data-inventory-search-error",
                "data-intro-panel=\"inventory-prices\"", "<h1 class=\"cl-page-title\">Prices and availability</h1>",
                "<script src=\"/js/inventory.js\" defer></script>");
    }

    @Test
    void priceComparisonHasNoTilesNoSourcesBarNoAddingAndStaysNarrow() {
        // when
        String html = prices(false, null);

        // then
        assertThat(html).doesNotContain("data-inventory-summary", "cl-inv-sources", "is-wide", "data-cl-list-results",
                "inventory-add-form", "/dashboard/inventory/add");
    }

    @Test
    void priceComparisonPromisesTheSuperAdminNoWarehouse() {
        // when
        String store = prices(false, null);
        String superAdmin = prices(true, null);

        // then
        assertThat(store).contains("Price, delivered cost and availability of one product at every supplier and in your warehouse.")
                .doesNotContain("without store warehouses");
        assertThat(superAdmin).contains(
                "Price and availability of a product at every global supplier (without store warehouses).");
    }

    @Test
    void supplierAssortmentCarriesTheTilesAndTheBrowseListWithoutTheCodeSearch() {
        // when
        String html = assortment(true);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-inventory-summary", "data-cl-list-results", "cl-page cl-page-body is-wide",
                "<h1 class=\"cl-page-title\">Assortment</h1>");
        assertThat(html).doesNotContain("data-inventory-search", "id=\"inventory-results\"");
    }

    @Test
    void neitherPageHasTheModeSwitchAnyMore() {
        // when
        String html = assortment(true) + prices(false, null);

        // then
        assertThat(html).doesNotContain("cl-inv-modes", "view=browse");
    }

    @Test
    void everyVisitorOfTheAssortmentGetsTheListTheRowMenuAndThePathScripts() {
        // when
        String html = assortment(false);

        // then
        assertThat(html).contains("<script src=\"/js/list-page.js\" defer></script>", "<script src=\"/js/menu.js\" defer></script>",
                "<script src=\"/js/collapse-path.js\" defer></script>");
        assertThat(html).doesNotContain("/js/dialog.js");
    }

    @Test
    void everyRoleGetsTheScriptThatReloadsTheListOnceTheIndexIsBuilt() {
        // when
        String user = assortment(false);
        String admin = assortment(true);

        // then
        assertThat(user).contains("<script src=\"/js/inventory-building.js\" defer></script>");
        assertThat(admin).contains("<script src=\"/js/inventory-building.js\" defer></script>");
    }

    /** "Dodaj do katalogu" is a link and a form post to "Uzupełnij dane": the page loads no dialog for it. */
    @Test
    void storeAdminPageHasNoAddDialogAndNoDialogScript() {
        // when
        String html = assortment(true);

        // then
        assertThat(html).doesNotContain("<dialog", "/js/dialog.js", "data-browse-dialog-url", "data-browse-dialog-slot");
    }

    private String prices(boolean superAdmin, String query) {
        Context context = new Context();
        context.setVariable("superAdmin", superAdmin);
        context.setVariable("query", query);
        return engine.process("inventory-prices", context);
    }

    private String assortment(boolean admin) {
        Context context = new Context();
        context.setVariable("superAdmin", false);
        context.setVariable("canManageSuppliers", admin);
        context.setVariable("manageSuppliersUrl", "/dashboard/store/suppliers");
        context.setVariable("browse", BrowsePage.of(BrowsePage.Status.READY, BrowseQuery.start(), admin));
        return engine.process("inventory", context);
    }
}
