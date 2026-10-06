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
    void categoryPageRendersRowsWithCategoryLinesAndTheAddAction() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(false)))));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Komponenty komputerowe", "Karty graficzne", "Podzespoły › Karta graficzna", "+1");
        assertThat(html).contains("data-browse-add", "data-cl-select-row", "data-cl-list-results");
        assertThat(html).contains("Availability: in stock");
    }

    @Test
    void productAlreadyInACatalogShowsTheInCatalogLinkInsteadOfTheAction() {
        // when
        String html = engine.process(RESULTS, context(page(true, false, BrowseQuery.start().withCategory("11"), List.of(row(true)))));

        // then
        assertThat(html).contains("In catalog", "/dashboard/catalogs/c-1/category/cat-gpu/products/p-1");
        assertThat(html).doesNotContain("data-browse-add");
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
        assertThat(html).contains("You have no suppliers switched on");
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
                List.of(new AddToCatalogDialog.Option("c-1/cat-gpu", "Podzespoły › Karta graficzna", 0)),
                List.of(new AddToCatalogDialog.Group("Podzespoły", List.of(new AddToCatalogDialog.Option("c-1/cat-case", "Obudowa", 0)))),
                false, "/dashboard/inventory?view=browse&cat=11", AddToCatalogDialog.ACTION);
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

    private static Context context(BrowsePage page) {
        Context context = new Context();
        context.setVariable("browse", page);
        context.setVariable("canManageSuppliers", true);
        context.setVariable("manageSuppliersUrl", "/dashboard/store/suppliers");
        return context;
    }

    private static BrowsePage.RowView row(boolean inCatalog) {
        CategoryLine line = new CategoryLine(List.of("Komponenty komputerowe"), "Karty graficzne",
                "Komponenty komputerowe › Karty graficzne", "Podzespoły › Karta graficzna", 1, false,
                inCatalog ? "/dashboard/catalogs/c-1/category/cat-gpu/products/p-1" : null);
        return new BrowsePage.RowView("Gigabyte RTX 4060", "Gigabyte", "5901000000001", "GV-N4060", "/dashboard/inventory?q=5901000000001",
                line, 1189.0, true, "AB", 214, 4, "/dashboard/inventory?view=browse&open=add&ean=5901000000001");
    }

    private static BrowsePage page(boolean admin, boolean noSuppliers, BrowseQuery query, List<BrowsePage.RowView> rows) {
        BrowsePage.SortHeader none = new BrowsePage.SortHeader("/dashboard/inventory?view=browse&sort=cost", "none");
        return new BrowsePage(query, admin, false, noSuppliers, false, query.isStart() ? null : "Karty graficzne",
                List.of(new BrowsePage.Crumb(null, "inventory.browse.all", "/dashboard/inventory?view=browse"),
                        new BrowsePage.Crumb("Karty graficzne", null, null)),
                List.of(new BrowsePage.NavItem("Karty graficzne", null, 3, "/dashboard/inventory?view=browse&cat=11", true)), true,
                query.isStart() ? List.of(new BrowsePage.Tile("Komponenty komputerowe", null, 5, "Karty graficzne",
                        "/dashboard/inventory?view=browse&cat=10")) : List.of(),
                List.of(new BrowsePage.MenuOption("AB", "AB", null, 3, false, null)),
                List.of(new BrowsePage.MenuOption("ALL", null, "inventory.browse.stock.ALL", 0, false, "/dashboard/inventory?view=browse"),
                        new BrowsePage.MenuOption("IN_STOCK", null, "inventory.browse.stock.IN_STOCK", 0, true, "/dashboard/inventory?view=browse&stock=in-stock")),
                admin ? List.of(new BrowsePage.MenuOption("ALL", null, "inventory.browse.catalog.ALL", 0, true, "/dashboard/inventory?view=browse")) : List.of(),
                List.of(new BrowsePage.Chip("inventory.browse.chip.stock", null, "inventory.browse.stock.IN_STOCK", "/dashboard/inventory?view=browse")),
                "/dashboard/inventory?view=browse", rows, rows.size(), false,
                Pagination.of(1, rows.size(), BrowseQuery.PAGE_SIZE, p -> "/dashboard/inventory?view=browse&page=" + p),
                Map.of("NAME", none, "COST", none, "QTY", none), query.href());
    }
}
