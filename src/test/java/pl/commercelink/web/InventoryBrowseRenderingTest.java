package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.inventory.BrowseCriteria;
import pl.commercelink.web.inventory.AddToCatalogDialog;
import pl.commercelink.web.inventory.BrowsePage;
import pl.commercelink.web.inventory.BrowseQuery;
import pl.commercelink.web.inventory.CategoryLine;
import pl.commercelink.web.orders.Pagination;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryBrowseRenderingTest {

    private static final String RESULTS = "<div th:replace=\"~{fragments/inventory-browse :: results}\"></div>";
    private static final String DIALOG = "<div th:replace=\"~{fragments/inventory-browse :: addDialog}\"></div>";

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void categoryPageRendersRowsWithThePimCategoryAndTheAddAction() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains(">PIM category</th>", "data-label=\"PIM category\"", "Komponenty komputerowe", "Karty graficzne");
        assertThat(html).contains("data-browse-add", "data-cl-select-row", "data-cl-list-results");
        assertThat(html).doesNotContain("Podzespoły › Karta graficzna", "+1", "Fits:", "No matching catalog category", "fa-book");
    }

    @Test
    void toolbarHasTheSupplierMenuAndSearchButNoAvailabilityOrCatalogFilter() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).contains("data-cl-filter-menu=\"supplier\"", "name=\"q2\"", "Supplier: AB");
        assertThat(html).doesNotContain("data-cl-filter-menu=\"stock\"", "data-cl-filter-menu=\"catalog\"", "Availability");
    }

    @Test
    void rowWithWarehouseStockShowsTheWarehouseLineUnderTheSupplierQuantity() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"),
                List.of(row(false, 6)))));

        // then
        assertThat(html).contains("Suppliers: 4", "In warehouse: 6");
        assertThat(html.indexOf("In warehouse: 6")).isGreaterThan(html.indexOf("Suppliers: 4"));
    }

    @Test
    void rowWithoutWarehouseStockHasNoWarehouseLine() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).contains("Suppliers: 4");
        assertThat(html).doesNotContain("In warehouse");
    }

    @Test
    void catalogStatusHasAVisibleHeaderAndTheActionsColumnAVisuallyHiddenOne() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).contains("<th scope=\"col\" class=\"cl-inv-catalog-cell\">In catalog</th>",
                "data-label=\"In catalog\"",
                "<th scope=\"col\" class=\"cl-table-actions\"><span class=\"cl-visually-hidden\">Actions</span></th>");
        assertThat(html.indexOf(">In catalog</th>")).isGreaterThan(html.indexOf(">Available</a>"));
        assertThat(html).doesNotContain(">Catalog</th>", "cl-inv-add");
    }

    @Test
    void productOutsideTheCatalogHasAnEmptyStatusCellAndOnlyTheAddItemInItsRowMenu() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).containsPattern("<td class=\"cl-inv-catalog-cell is-empty\"\\s+data-label=\"In catalog\">\\s*"
                + "<span class=\"cl-visually-hidden\">Not in a catalog</span>\\s*</td>");
        assertThat(html).contains("<details class=\"cl-menu\">",
                "<summary class=\"cl-button is-icon\" aria-label=\"Actions: Gigabyte RTX 4060\">",
                "<span class=\"cl-menu-glyph\" aria-hidden=\"true\">⋯</span>");
        assertThat(html).contains("<a class=\"cl-menu-item\" href=\"/dashboard/inventory?open=add&amp;ean=5901000000001\" "
                + "data-browse-add data-ean=\"5901000000001\">Add to catalog</a>");
        assertThat(html.split("class=\"cl-menu-item\"", -1)).hasSize(2);
        assertThat(html).doesNotContain("Prices and availability", "cl-mark", "fa-check-circle", "Open in catalog",
                "Add to another category");
    }

    @Test
    void productInTheCatalogShowsAFocusableCheckIconWithItsPlacesOnePerLineAndNoLink() {
        // given
        BrowsePage.RowView row = row(line(List.of("Podzespoły › Karta graficzna", "Sklep B2B › Karty")), 0);

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row))));

        // then
        String places = "In catalog:\nPodzespoły › Karta graficzna\nSklep B2B › Karty";
        assertThat(html).contains("<span class=\"cl-inv-catalog-mark\"><span class=\"cl-mark is-positive cl-tooltip is-lines is-end\" "
                + "tabindex=\"0\" role=\"img\" data-tooltip=\"" + places + "\" aria-label=\"" + places + "\">"
                + "<i class=\"fas fa-check-circle\" aria-hidden=\"true\"></i></span></span>");
        assertThat(html).doesNotContain("<a class=\"cl-mark", "Not in a catalog", "Karta graficzna</span>", "+1",
                "cl-inv-catalog-cell is-empty");
        assertThat(html).containsPattern("<td class=\"cl-inv-catalog-cell\"\\s+data-label=\"In catalog\">");
    }

    @Test
    void rowMenuOfAProductInTheCatalogOnlyAddsElsewhereBecauseTheNameAlreadyLeadsToThePrices() {
        // given
        BrowsePage.RowView row = row(line(List.of("Podzespoły › Karta graficzna")), 0);

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row))));

        // then
        assertThat(html).contains("data-browse-add data-ean=\"5901000000001\">Add to another category</a>",
                "<a href=\"/dashboard/inventory/prices?q=5901000000001\">Gigabyte RTX 4060</a>");
        assertThat(html.split("class=\"cl-menu-item\"", -1)).hasSize(2);
        assertThat(html).doesNotContain(">Add to catalog</a>", "Prices and availability", "Open in catalog", "/dashboard/catalogs/");
    }

    @Test
    void countLineIsOnlyForScreenReadersWithoutChips() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"),
                List.of(row(false)), List.of())));

        // then
        assertThat(html).contains("<div class=\"cl-list-meta is-count-only\">");
        assertThat(html).containsPattern("<p class=\"cl-table-results cl-visually-hidden\" role=\"status\">\\s*"
                + "<span>Products: 1</span>");
        assertThat(html).doesNotContain("page 1 of", "cl-filter-chips");
    }

    @Test
    void countLineStandsVisibleNextToTheChips() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).contains("cl-filter-chips", "Supplier: AB");
        assertThat(html).containsPattern("<p class=\"cl-table-results\" role=\"status\">\\s*<span>Products: 1</span>");
        assertThat(html).doesNotContain("cl-table-results cl-visually-hidden", "page 1 of");
    }

    @Test
    void nonAdminSeesNeitherSelectionNorCatalogStatusNorRowMenu() {
        // when
        String html = engine.process(RESULTS, context(page(false, false, BrowseQuery.start().withCategory("11"),
                List.of(row(false), row(true)))));

        // then
        assertThat(html).doesNotContain("data-browse-add", "data-cl-select-row", "cl-inv-in-catalog", "Not in a catalog",
                "In catalog", "cl-menu", "Actions");
    }

    @Test
    void noSuppliersShowsTheAlert() {
        // when
        String html = engine.process(RESULTS, context(page(true, true, BrowseQuery.start(), List.of())));

        // then
        assertThat(html).contains("There are no products to browse yet");
        assertThat(html).doesNotContain("cl-table");
    }

    @Test
    void buildingIndexShowsABusySkeletonWithAStatusInsteadOfTheNoProductsMessage() {
        // when
        String html = engine.process(RESULTS, context(BrowsePage.of(BrowsePage.Status.BUILDING, BrowseQuery.start(), true, false)));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("aria-busy=\"true\"", "data-browse-building", "cl-skeleton",
                "<p role=\"status\">Preparing the assortment for browsing \u2014 this takes a few seconds.</p>",
                "<noscript><p class=\"cl-inv-muted\">Reload the page in a moment.</p></noscript>", "data-cl-list-results");
        assertThat(html).doesNotContain("There are no products to browse yet", "cl-table", "No products for these filters");
    }

    @Test
    void unavailablePimShowsTheErrorAlertWithARetryOfTheSameAddress() {
        // given
        BrowseQuery query = BrowseQuery.start().withCategory("11");

        // when
        String html = engine.process(RESULTS, context(BrowsePage.of(BrowsePage.Status.PIM_UNAVAILABLE, query, true, false)));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("<div class=\"cl-alert is-bad\" role=\"alert\">", "Could not load the PIM categories.",
                "<a href=\"/dashboard/inventory?cat=11\">Try again</a>");
        assertThat(html).doesNotContain("There are no products to browse yet", "cl-table", "data-browse-building");
    }

    @Test
    void unknownCategoryShowsItsOwnEmptyStateWithAWayToTheStart() {
        // when
        String html = engine.process(RESULTS, context(BrowsePage.of(BrowsePage.Status.UNKNOWN_CATEGORY,
                BrowseQuery.start().withCategory("999999999"), true, false)));

        // then
        assertThat(html).doesNotContain("??", "999999999");
        assertThat(html).contains("<h2 id=\"browse-title\" tabindex=\"-1\">There is no such category</h2>",
                "The PIM has no such category, or it was removed.",
                "<a class=\"cl-link-button\" href=\"/dashboard/inventory\" data-cl-list-nav>All categories</a>");
        assertThat(html).contains("<div class=\"cl-list-empty\">");
        assertThat(html).doesNotContain("There are no products to browse yet", "cl-table");
    }

    @Test
    void rowWithoutAnEanHasNeitherTheCheckboxNorTheRowMenu() {
        // given
        BrowsePage.RowView noEan = new BrowsePage.RowView("Zasilacz", "Brand", null, "PSU-1",
                "/dashboard/inventory/prices?q=PSU-1", line(List.of()), 199.0, true, "AB", 3, 1, 0, null);

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"),
                List.of(noEan, row(false)))));

        // then
        assertThat(html.split("data-cl-select-row", -1)).hasSize(2);
        assertThat(html.split("<details class=\"cl-menu\">", -1)).hasSize(2);
        assertThat(html).contains("value=\"5901000000001\"", "PSU-1");
        assertThat(html).doesNotContain("Actions: Zasilacz", "Select: Zasilacz");
    }

    @Test
    void startRendersCategoryTiles() {
        // given
        BrowsePage start = page(true, false, BrowseQuery.start(), List.of());

        // when
        String html = engine.process(RESULTS, context(start));

        // then
        assertThat(html).contains("cl-tile-grid", "Komponenty komputerowe", "Products: 5");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void dialogRendersMatchingOptionsTheOtherSelectAndTheChosenEans() {
        // given
        AddToCatalogDialog dialog = new AddToCatalogDialog(List.of("5901000000001", "5901000000002"), null, "Karty graficzne",
                List.of(new AddToCatalogDialog.Option("c-1/cat-gpu", "Podzespoły › Karta graficzna", 0, true)),
                List.of(new AddToCatalogDialog.Group("Podzespoły", List.of(new AddToCatalogDialog.Option("c-1/cat-case", "Obudowa", 0, false)))),
                false, "/dashboard/inventory?cat=11", AddToCatalogDialog.ACTION);
        Context context = new Context();
        context.setVariable("addDialog", dialog);

        // when
        String html = engine.process(DIALOG, context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Add to catalog (2)", "value=\"c-1/cat-gpu\"", "data-browse-other-select", "value=\"c-1/cat-case\"");
        assertThat(html).contains("name=\"eans\" value=\"5901000000001\"", "name=\"returnTo\"");
        assertThat(html).doesNotContain(" open");
    }

    @Test
    void dialogChecksThePreselectedOptionEvenWhenItIsNotTheFirst() {
        // given
        AddToCatalogDialog dialog = new AddToCatalogDialog(List.of("5901000000001"), "RTX 4060", "Karty graficzne",
                List.of(new AddToCatalogDialog.Option("c-1/cat-gpu", "Podzespoły › Karta graficzna", 1, false),
                        new AddToCatalogDialog.Option("c-2/cat-b2b", "Sklep B2B › Karty", 0, true)),
                List.of(), false, "/dashboard/inventory?cat=11", AddToCatalogDialog.ACTION);
        Context context = new Context();
        context.setVariable("addDialog", dialog);

        // when
        String html = engine.process(DIALOG, context);

        // then
        assertThat(html).containsPattern("value=\"c-2/cat-b2b\"\\s+checked");
        assertThat(html).doesNotContainPattern("value=\"c-1/cat-gpu\"\\s+checked");
    }

    @Test
    void categoryNamesBreakAfterEverySlashOnTheTileInTheHeadAndInThePills() {
        // given
        String name = "Profesjonalne/konsumenckie AV i foto";
        String broken = "Profesjonalne/<wbr>konsumenckie AV i foto";

        // when
        String start = engine.process(RESULTS, context(page(true, false, BrowseQuery.start(), List.of(), List.of(), name, name)));
        String category = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"),
                List.of(row(false)), List.of(), name, name)));

        // then
        assertThat(start).contains("<span class=\"cl-tile-title\">" + broken + "</span>");
        assertThat(category).contains("<h2 id=\"browse-title\" tabindex=\"-1\">" + broken + "</h2>",
                "<span aria-current=\"page\">" + broken + "</span>", "<span>" + broken + "</span>");
        assertThat(start + category).doesNotContain("Profesjonalne/konsumenckie");
    }

    @Test
    void categoryPathBreaksAfterSlashesKeepsEdgeSlashesAndStaysEscaped() {
        // given
        CategoryLine line = new CategoryLine(List.of("Audio/wideo"), "/<b>x</b>//y/", "Audio/wideo › /<b>x</b>//y/", List.of());

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(line, 0)))));

        // then
        assertThat(html).contains("<span>Audio/<wbr>wideo</span> › <strong>/<wbr>&lt;b&gt;x&lt;/<wbr>b&gt;/<wbr>/<wbr>y/<wbr></strong>",
                "title=\"Audio/wideo › /&lt;b&gt;x&lt;/b&gt;//y/\"");
        assertThat(html).doesNotContain("<b>x</b>");
    }

    @Test
    void longCategoryPathIsRenderedWholeWithEachCrumbCarryingItsSeparatorForTheScriptToCollapse() {
        // given
        List<BrowsePage.Crumb> crumbs = List.of(new BrowsePage.Crumb(null, "inventory.browse.all", "/dashboard/inventory"),
                new BrowsePage.Crumb("Komputery", null, "/dashboard/inventory?cat=1"),
                new BrowsePage.Crumb("Komponenty", null, "/dashboard/inventory?cat=2"),
                new BrowsePage.Crumb("Chłodzenie", null, "/dashboard/inventory?cat=3"),
                new BrowsePage.Crumb("Wentylatory", null, null));

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("4"), List.of(row(false)),
                List.of(), "Wentylatory", "Komputery", crumbs)));

        // then
        assertThat(html).contains("<p class=\"cl-result-path\" data-cl-collapse-path data-cl-collapse-label=\"Show the full category path\">");
        assertThat(html.split("data-cl-path-crumb", -1)).hasSize(6);
        assertThat(html).contains("<span class=\"cl-path-crumb\" data-cl-path-crumb><a href=\"/dashboard/inventory?cat=2\" data-cl-list-nav>"
                        + "Komponenty</a><span aria-hidden=\"true\"> › </span></span>",
                "<span class=\"cl-path-crumb\" data-cl-path-crumb><span aria-current=\"page\">Wentylatory</span></span>",
                ">All categories</a>");
        assertThat(html).doesNotContain("data-cl-collapsed", "cl-path-more", "hidden>");
    }

    @Test
    void pimCategoryIsBothASecondaryColumnAndALineUnderTheProductForNarrowScreens() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).contains("<th scope=\"col\" class=\"is-secondary-column\">PIM category</th>");
        assertThat(html).containsPattern("<td class=\"cl-category-cell is-secondary-column\" data-label=\"PIM category\">");
        String key = html.substring(html.indexOf("<th scope=\"row\" class=\"cl-table-key\">"), html.indexOf("</th>",
                html.indexOf("<th scope=\"row\" class=\"cl-table-key\">")));
        assertThat(key).contains("<span class=\"cl-table-sub cl-narrow-only\">", "Komponenty komputerowe", "<strong>Karty graficzne</strong>");
        assertThat(key).contains("<span class=\"cl-table-code\">5901000000001</span>", "<span class=\"cl-table-code\">GV-N4060</span>");
    }

    @Test
    void costHasAShortFieldNameOnTheCard() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).contains(">Lowest delivered cost</a>", "data-label=\"Delivered cost\"");
        assertThat(html).doesNotContain("data-label=\"Lowest delivered cost\"");
    }

    @Test
    void emptyResultIsOnlyItsTextBecauseClearingTheFiltersIsInTheToolbar() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of())));

        // then
        assertThat(html).contains("<p class=\"cl-list-empty\">No products for these filters.</p>");
        assertThat(html.split(">Clear filters</a>", -1)).hasSize(2);
        assertThat(html).doesNotContain("<table", "cl-inv-browse-empty");
    }

    @Test
    void catalogAndCategoryNamesAreEscapedInTheCheckTooltip() {
        // given
        CategoryLine line = line(List.of("<b>x</b> › <b>Fan</b>", "Sklep › <b>y</b>"));

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(line, 0)))));

        // then
        String escaped = "In catalog:\n&lt;b&gt;x&lt;/b&gt; › &lt;b&gt;Fan&lt;/b&gt;\nSklep › &lt;b&gt;y&lt;/b&gt;";
        assertThat(html).contains("data-tooltip=\"" + escaped + "\"", "aria-label=\"" + escaped + "\"");
        assertThat(html).doesNotContain("<b>x</b>", "<b>y</b>", "<b>Fan</b>");
    }

    private static Context context(BrowsePage page) {
        Context context = new Context();
        context.setVariable("browse", page);
        context.setVariable("canManageSuppliers", true);
        context.setVariable("manageSuppliersUrl", "/dashboard/store/suppliers");
        return context;
    }

    private static BrowsePage.RowView row(boolean inCatalog) {
        return row(inCatalog, 0);
    }

    private static BrowsePage.RowView row(boolean inCatalog, long warehouseQty) {
        return row(line(inCatalog ? List.of("Podzespoły › Karta graficzna", "Sklep B2B › Karty") : List.of()), warehouseQty);
    }

    private static CategoryLine line(List<String> inCatalogLabels) {
        return new CategoryLine(List.of("Komponenty komputerowe"), "Karty graficzne",
                "Komponenty komputerowe › Karty graficzne", inCatalogLabels);
    }

    private static BrowsePage.RowView row(CategoryLine line, long warehouseQty) {
        return new BrowsePage.RowView("Gigabyte RTX 4060", "Gigabyte", "5901000000001", "GV-N4060", "/dashboard/inventory/prices?q=5901000000001",
                line, 1189.0, true, "AB", 214, 4, warehouseQty, "/dashboard/inventory?open=add&ean=5901000000001");
    }

    private static BrowsePage page(boolean admin, boolean noSuppliers, BrowseQuery query, List<BrowsePage.RowView> rows) {
        return page(admin, noSuppliers, query, rows,
                List.of(new BrowsePage.Chip("inventory.browse.chip.supplier", "AB", "/dashboard/inventory")));
    }

    private static BrowsePage page(boolean admin, boolean noSuppliers, BrowseQuery query, List<BrowsePage.RowView> rows,
                                   List<BrowsePage.Chip> chips) {
        return page(admin, noSuppliers, query, rows, chips, "Karty graficzne", "Komponenty komputerowe");
    }

    private static BrowsePage page(boolean admin, boolean noSuppliers, BrowseQuery query, List<BrowsePage.RowView> rows,
                                   List<BrowsePage.Chip> chips, String category, String tile) {
        return page(admin, noSuppliers, query, rows, chips, category, tile,
                List.of(new BrowsePage.Crumb(null, "inventory.browse.all", "/dashboard/inventory"),
                        new BrowsePage.Crumb(category, null, null)));
    }

    private static BrowsePage page(boolean admin, boolean noSuppliers, BrowseQuery query, List<BrowsePage.RowView> rows,
                                   List<BrowsePage.Chip> chips, String category, String tile, List<BrowsePage.Crumb> crumbs) {
        BrowsePage.SortHeader none = new BrowsePage.SortHeader("/dashboard/inventory?sort=cost", "none");
        return new BrowsePage(query, admin, false, noSuppliers, false, query.isStart() ? null : category,
                crumbs,
                List.of(new BrowsePage.NavItem(category, null, 3, "/dashboard/inventory?cat=11", true)), true,
                query.isStart() ? List.of(new BrowsePage.Tile(tile, null, 5, "Karty graficzne",
                        "/dashboard/inventory?cat=10")) : List.of(),
                List.of(new BrowsePage.MenuOption("AB", "AB", 3, true)),
                chips,
                "/dashboard/inventory", rows, rows.size(), false,
                Pagination.of(1, rows.size(), BrowseQuery.PAGE_SIZE, p -> "/dashboard/inventory?page=" + p),
                Map.of("NAME", none, "COST", none, "QTY", none), query.href(), BrowsePage.Status.READY);
    }
}
