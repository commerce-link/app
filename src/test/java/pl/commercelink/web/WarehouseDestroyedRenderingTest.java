package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.thymeleaf.context.Context;
import pl.commercelink.warehouse.builtin.DestroyedListQuery;
import pl.commercelink.warehouse.builtin.DestroyedPageModel;
import pl.commercelink.warehouse.builtin.WarehouseItemRow;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Chip;
import pl.commercelink.warehouse.builtin.WarehousePageModel.EmptyState;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Option;
import pl.commercelink.warehouse.builtin.WarehousePageModel.SortHeader;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Tile;
import pl.commercelink.web.orders.Pagination;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the results fragment of the destroyed archive through real Thymeleaf with English messages. */
class WarehouseDestroyedRenderingTest {

    private static WarehouseItemRow item(String id, String deliveryHref, String serialNo) {
        return new WarehouseItemRow(id, "/dashboard/warehouse/items/" + id + "?back=%2Fdashboard%2Fwarehouse%2Fitems%2Fdestroyed",
                "SSD " + id, List.of("EAN 8806094215038", "MZ-V9P2T0BW"), "Damaged", "is-bad", "Flooded", "SSD", false, 1,
                "689,00", "847,47 gross", null, deliveryHref, deliveryHref != null ? "3F2A9C1E" : null, null, serialNo,
                "Destroyed", "Destroyed", "is-muted", false, deliveryHref != null);
    }

    private static DestroyedPageModel model(List<DestroyedPageModel.Row> rows, boolean storeEmpty, EmptyState emptyState) {
        Map<DestroyedListQuery.Sort, SortHeader> headers = new EnumMap<>(DestroyedListQuery.Sort.class);
        for (DestroyedListQuery.Sort s : DestroyedListQuery.Sort.values()) {
            headers.put(s, new SortHeader("#", "none"));
        }
        return new DestroyedPageModel(DestroyedListQuery.parse(new LinkedMultiValueMap<>()),
                List.of(new Tile("This month", "689,00", "PLN", "1 pcs · net", "/dashboard/warehouse/items/destroyed?period=month", false),
                        new Tile("Last month", "7 043,00", "PLN", "8 pcs · net", "/dashboard/warehouse/items/destroyed?period=previous", false),
                        new Tile("Last 12 months", "7 732,00", "PLN", "9 pcs · net", "/dashboard/warehouse/items/destroyed", true),
                        new Tile("All", "12 954,00", "PLN", "17 pcs · net", "/dashboard/warehouse/items/destroyed?period=all", false)),
                "Last 12 months", null, null,
                List.of(new Option("SSD", "SSD", 1, false, "/dashboard/warehouse/items/destroyed?categories=SSD")), "All",
                List.of(new Chip("Destroyed: Last 12 months", "/dashboard/warehouse/items/destroyed?period=all", "Remove filter")),
                "Products: 2, Units: 2 · Net value: 1 378,00 PLN", headers, rows, Pagination.of(1, 2, 50, n -> "?page=" + n),
                emptyState, storeEmpty, 1);
    }

    private static DestroyedPageModel pageWithRows() {
        return model(List.of(
                new DestroyedPageModel.Row(item("a1", "/dashboard/deliveries/details?deliveryId=d1", "S1"), "06.10.2026",
                        "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=d1&mfn=MZ-V9P2T0BW",
                        "/dashboard/item/history?serialNo=S1", "Actions: SSD a1"),
                new DestroyedPageModel.Row(item("b2", null, null), "02.10.2026", null, null, "Actions: SSD b2")), false, null);
    }

    private static DestroyedPageModel emptyStorePage() {
        return model(List.of(), true, new EmptyState("Nothing has been destroyed yet.", null, null));
    }

    private static String render(DestroyedPageModel page) {
        Context context = new Context();
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{warehouse-destroyed :: results}\"></div>", context);
    }

    @Test
    void tableHasNoCheckboxesAndADateAndMenuColumn() {
        // when
        String html = render(pageWithRows());

        // then
        assertThat(html).contains("cl-table is-warehouse is-destroyed").doesNotContain("data-cl-select-row").doesNotContain("??");
        assertThat(html).contains("06.10.2026").contains("class=\"cl-col-date\"").contains("class=\"cl-col-menu\"");
        assertThat(html).contains("delivery-mfn-history").contains("/dashboard/item/history?serialNo=S1");
        assertThat(html).contains("aria-disabled=\"true\"").contains("Item without a delivery");
        assertThat(html).contains("data-cl-list-path=\"/dashboard/warehouse/items/destroyed\"");
    }

    @Test
    void allFourTilesAreLinksWithTheirUnitAndTheActiveOneIsMarked() {
        // when
        String html = render(pageWithRows());

        // then
        assertThat(html.split("<a class=\"cl-stat is-link\"", -1)).hasSize(5);
        assertThat(html).contains("<span class=\"cl-stat-value-of\">PLN</span>").contains("aria-current=\"true\"");
        assertThat(html).contains("data-cl-filter-menu=\"dates\"").contains("type=\"date\"").contains("data-cl-filter-menu=\"categories\"");
    }

    @Test
    void emptyStoreShowsTheSentenceWithoutAnAction() {
        // when
        String html = render(emptyStorePage());

        // then
        assertThat(html).contains("Nothing has been destroyed yet.").doesNotContain("<table");
    }

    @Test
    void savedMessageOfAnItemOpenedFromTheArchiveIsShownOnReturn() {
        // given
        Context context = new Context();
        context.setVariable("page", pageWithRows());
        context.setVariable("settingsSavedMessage", "Item SSD a1 saved.");

        // when
        String html = EnglishFragmentTemplateEngine.create().process("warehouse-destroyed", context);

        // then
        assertThat(html).contains("data-cl-saved-alert").contains("Item SSD a1 saved.");
        assertThat(html.indexOf("data-cl-saved-alert")).isLessThan(html.indexOf("data-cl-list-results"));
    }

    @Test
    void oldTemplatesAreGone() {
        // when / then
        assertThat(Files.exists(Path.of("src/main/resources/templates/destroyedWarehouseItems.html"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/resources/templates/fragments/warehouseItemsListFragment.html"))).isFalse();
    }
}
