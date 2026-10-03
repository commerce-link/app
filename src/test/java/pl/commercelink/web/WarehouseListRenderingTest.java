package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.thymeleaf.context.Context;
import pl.commercelink.warehouse.builtin.WarehouseItemRow;
import pl.commercelink.warehouse.builtin.WarehouseListQuery;
import pl.commercelink.warehouse.builtin.WarehousePageModel;
import pl.commercelink.warehouse.builtin.WarehousePageModel.BulkActionView;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Chip;
import pl.commercelink.warehouse.builtin.WarehousePageModel.EmptyState;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Option;
import pl.commercelink.warehouse.builtin.WarehousePageModel.SortHeader;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Tile;
import pl.commercelink.web.orders.Pagination;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the results fragment of the warehouse list through real Thymeleaf with English messages. */
class WarehouseListRenderingTest {

    private static WarehouseItemRow row(String id, String status, boolean selectable) {
        return new WarehouseItemRow(id, "/dashboard/warehouse/items/" + id, "RTX " + id, "EAN 590 · GV-1", "Damaged", "is-bad",
                "comment " + id, "GPU", false, 3, "1 243,00", "gross 1 528,89", null, null, "/dashboard/deliveries/details?deliveryId=d1",
                "d1", "S/N 1", status, status, "is-ok", selectable, "Acme");
    }

    private static WarehousePageModel model(List<WarehouseItemRow> rows, boolean storeEmpty) {
        Map<WarehouseListQuery.Sort, SortHeader> headers = new EnumMap<>(WarehouseListQuery.Sort.class);
        for (WarehouseListQuery.Sort s : WarehouseListQuery.Sort.values()) {
            headers.put(s, new SortHeader("/dashboard/warehouse?sort=" + s.param(), "none"));
        }
        BulkActionView reserve = new BulkActionView("reserve", "/dashboard/warehouse/markAsReserved", "Reserve", "Delivered",
                true, false, false, false, "Only for: In stock", null, null, "Reserve");
        BulkActionView destroy = new BulkActionView("destroy", "/dashboard/warehouse/markAsDestroyed", "Destroy",
                "Delivered InRMA InExternalService", true, false, false, true, "Only for: …", null, null, "Destroy");
        return new WarehousePageModel(WarehouseListQuery.parse(new LinkedMultiValueMap<>(), false), true, false,
                List.of(new Tile("In stock", "3 pcs", "in 1 items", "/dashboard/warehouse", true)),
                List.of(new Option("Delivered", "In stock", 1, true, "/dashboard/warehouse?statuses=all")), "In stock",
                List.of(new Option("GPU", "GPU", 1, false, "/dashboard/warehouse?categories=GPU"),
                        new Option("none", "No category", 0, false, "/x")), "All",
                List.of(new Chip("Status: In stock", "/dashboard/warehouse?statuses=all", "Remove filter: Status: In stock")),
                "Items: 2 · 6 pcs", headers, rows, Pagination.of(1, rows.size(), 50, n -> "/p" + n),
                rows.isEmpty() ? new EmptyState(storeEmpty ? "The warehouse is empty." : "Nothing matches the filters.",
                        "Clear filters", "/dashboard/warehouse") : null,
                storeEmpty, 1, 7, List.of(reserve), destroy, List.of(new Option("Theft", "Theft", 0, false, null)));
    }

    private static String render(WarehousePageModel page) {
        Context context = new Context();
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{warehouse :: results}\"></div>", context);
    }

    @Test
    void rowsMenusChipsAndSelectionAreRendered() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true), row("b2", "Ordered", false)), false);

        // when
        String html = render(page);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-cl-list-results").contains("data-cl-list-fragment=\"/dashboard/warehouse/list\"");
        assertThat(html).contains("class=\"cl-stat is-link\"").contains("aria-current=\"true\"");
        assertThat(html).contains("data-cl-filter-menu=\"statuses\"").contains("data-cl-filter-menu=\"categories\"").contains("No category");
        assertThat(html).contains("Status: In stock").contains("Items: 2 · 6 pcs");
        assertThat(html).contains("cl-table is-warehouse").contains("data-cl-select-table=\"true\"");
        assertThat(html).contains("value=\"a1\"").doesNotContain("value=\"b2\"");
        assertThat(html).contains("data-status=\"Delivered\"").contains("data-qty=\"3\"").contains("data-source=\"Acme\"");
        assertThat(html).contains("data-cl-action-path=\"/dashboard/warehouse/markAsReserved\"").contains("data-cl-action-for=\"Delivered\"");
        assertThat(html).contains("Destroyed items: 7").contains("is-secondary-column");
        assertThat(html).contains("id=\"warehouse-bulk-form\"").contains("id=\"cl-quantity-dialog\"");
    }

    @Test
    void categoryCellIsMarkedForHyphenation() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("<td class=\"is-hyphenated\" data-label=\"Category\">");
    }

    @Test
    void wholePageRendersPastTheResultsBlock() {
        // given
        Context context = new Context();
        context.setVariable("page", model(List.of(row("a1", "Delivered", true)), false));

        // when
        String html = EnglishFragmentTemplateEngine.create().process("warehouse", context);

        // then
        assertThat(html).doesNotContain("??").contains("id=\"cl-confirm-dialog\"").contains("/js/selection-actions.js");
    }

    @Test
    void emptyStoreRendersNoToolbarNoTable() {
        // given
        WarehousePageModel page = model(List.of(), true);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("The warehouse is empty.").doesNotContain("<table").doesNotContain("data-cl-filter-menu");
    }

    @Test
    void noFilterMatchKeepsToolbarAndOffersClear() {
        // given
        WarehousePageModel page = model(List.of(), false);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("Nothing matches the filters.").contains("data-cl-filter-menu=\"statuses\"").doesNotContain("<table");
    }
}
