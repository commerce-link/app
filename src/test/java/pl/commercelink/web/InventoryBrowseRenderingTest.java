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
    void productAlreadyInACatalogShowsTheInCatalogLinkInsteadOfTheAction() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(true)))));

        // then
        assertThat(html).contains("In catalog", "/dashboard/catalogs/c-1/category/cat-gpu/products/p-1");
        assertThat(html).doesNotContain("data-ean=\"5901000000001\"");
    }

    @Test
    void productInOneOfTwoMatchingCategoriesSaysWhereAndOffersTheOther() {
        // given
        BrowsePage.RowView row = row(line(List.of("Podzespoły › Karta graficzna"), true), 0);

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row))));

        // then
        assertThat(html).contains("title=\"In catalog: Podzespoły › Karta graficzna\"", ">In catalog</a>");
        assertThat(html).contains("Add to another", "data-ean=\"5901000000001\"",
                "aria-label=\"Add to another catalog category: Gigabyte RTX 4060\"");
        assertThat(html).doesNotContain(">Add to catalog<");
    }

    @Test
    void productInEveryMatchingCategoryShowsTheCountAndNoFurtherAction() {
        // given
        BrowsePage.RowView row = row(line(List.of("Podzespoły › Karta graficzna", "Sklep B2B › Karty"), false), 0);

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row))));

        // then
        assertThat(html).contains("In catalog: 2", "title=\"In catalog: Podzespoły › Karta graficzna · Sklep B2B › Karty\"");
        assertThat(html).doesNotContain("Add to another", "data-ean=\"5901000000001\"");
    }

    @Test
    void nonAdminSeesNeitherSelectionNorAction() {
        // when
        String html = engine.process(RESULTS, context(page(false, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).doesNotContain("data-browse-add", "data-cl-select-row", "cl-category-catalog");
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
    void catalogAndCategoryNamesAreEscapedInThePillTooltip() {
        // given
        CategoryLine line = new CategoryLine(List.of("Komponenty komputerowe"), "Karty graficzne",
                "Komponenty komputerowe › Karty graficzne", "/dashboard/catalogs/c-1/category/cat-gpu/products/p-1",
                List.of("<b>x</b> › Fan", "Sklep › <b>y</b>"), true);

        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(line, 0)))));

        // then
        assertThat(html).contains("title=\"In catalog: &lt;b&gt;x&lt;/b&gt; › Fan · Sklep › &lt;b&gt;y&lt;/b&gt;\"");
        assertThat(html).doesNotContain("<b>x</b>", "<b>y</b>");
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
        return row(line(inCatalog ? List.of("Podzespoły › Karta graficzna", "Sklep B2B › Karty") : List.of(), false), warehouseQty);
    }

    private static CategoryLine line(List<String> inCatalogLabels, boolean addableElsewhere) {
        return new CategoryLine(List.of("Komponenty komputerowe"), "Karty graficzne",
                "Komponenty komputerowe › Karty graficzne",
                inCatalogLabels.isEmpty() ? null : "/dashboard/catalogs/c-1/category/cat-gpu/products/p-1",
                inCatalogLabels, addableElsewhere);
    }

    private static BrowsePage.RowView row(CategoryLine line, long warehouseQty) {
        return new BrowsePage.RowView("Gigabyte RTX 4060", "Gigabyte", "5901000000001", "GV-N4060", "/dashboard/inventory/prices?q=5901000000001",
                line, 1189.0, true, "AB", 214, 4, warehouseQty, "/dashboard/inventory?open=add&ean=5901000000001");
    }

    private static BrowsePage page(boolean admin, boolean noSuppliers, BrowseQuery query, List<BrowsePage.RowView> rows) {
        BrowsePage.SortHeader none = new BrowsePage.SortHeader("/dashboard/inventory?sort=cost", "none");
        return new BrowsePage(query, admin, false, noSuppliers, false, query.isStart() ? null : "Karty graficzne",
                List.of(new BrowsePage.Crumb(null, "inventory.browse.all", "/dashboard/inventory"),
                        new BrowsePage.Crumb("Karty graficzne", null, null)),
                List.of(new BrowsePage.NavItem("Karty graficzne", null, 3, "/dashboard/inventory?cat=11", true)), true,
                query.isStart() ? List.of(new BrowsePage.Tile("Komponenty komputerowe", null, 5, "Karty graficzne",
                        "/dashboard/inventory?cat=10")) : List.of(),
                List.of(new BrowsePage.MenuOption("AB", "AB", 3, true)),
                List.of(new BrowsePage.Chip("inventory.browse.chip.supplier", "AB", "/dashboard/inventory")),
                "/dashboard/inventory", rows, rows.size(), false,
                Pagination.of(1, rows.size(), BrowseQuery.PAGE_SIZE, p -> "/dashboard/inventory?page=" + p),
                Map.of("NAME", none, "COST", none, "QTY", none), query.href());
    }
}
