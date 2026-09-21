package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.inventory.InventoryStatistics;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.warehouse.api.StockSummary;
import pl.commercelink.web.inventory.InventorySourcesView;
import pl.commercelink.web.inventory.RelativeTime;
import pl.commercelink.web.inventory.SourceRow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventorySummaryRenderingTest {

    private static final String SUMMARY = "<div th:replace=\"~{fragments/inventory-summary :: summary}\"></div>";
    private static final String WAREHOUSE = "<ul><li th:replace=\"~{fragments/inventory-summary :: warehouseTile}\"></li></ul>";
    private static final LocalDateTime FEED = LocalDateTime.of(2026, 9, 14, 9, 0);

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private SourceRow working(String label) {
        return new SourceRow(label, label, ConnectionMode.GLOBAL, 12430, FEED, new RelativeTime("inventory.time.hours", 3), false);
    }

    private SourceRow missingFile(String label) {
        return new SourceRow(label, label, ConnectionMode.MANUAL, 0, null, null, true);
    }

    private Context context(InventorySourcesView sources, boolean canManageSuppliers) {
        Context context = new Context();
        context.setVariable("statistics", new InventoryStatistics(38755, 24102, Map.of()));
        context.setVariable("sources", sources);
        context.setVariable("canManageSuppliers", canManageSuppliers);
        context.setVariable("manageSuppliersUrl", "/dashboard/store/suppliers");
        context.setVariable("warehouseUrl", "/dashboard/warehouse");
        context.setVariable("connectionUrlPrefix", "/dashboard/store/suppliers/");
        return context;
    }

    @Test
    void rendersTheTilesAndAHealthySourcesBarWithTheManageShortcutForAdmin() {
        // given
        InventorySourcesView sources = new InventorySourcesView(List.of(), List.of(working("AB"), working("Elko")), 2, 3, false);

        // when
        String html = engine.process(SUMMARY, context(sources, true));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-inventory-fragment=\"summary\"");
        assertThat(html).contains("Products at suppliers").contains("38 755").contains("62% with stock above zero");
        assertThat(html).contains("data-inventory-slot=\"warehouse\"");
        assertThat(html).contains("href=\"/dashboard/store/suppliers\"");
        assertThat(html).contains("Products: 12,430").contains("feed 3 h ago");
        assertThat(html).containsPattern("data-inventory-source-count[^>]*>\\s*<span aria-hidden=\"true\">3</span>")
                .contains("Sources: 3");
        assertThat(html).contains("data-inventory-warehouse-products");
        assertThat(html).contains("Expand").contains("Collapse");
        assertThat(html).doesNotContain("All sources have a feed file").doesNotContain("Active suppliers:")
                .doesNotContain(">Working<").doesNotContain("Open the warehouse").doesNotContain("cl-inv-sources-footer");
    }

    /**
     * Each of these figures ends in a question the operator then wants to open; the "3 of 3" hint also
     * says what the tile counts, so it no longer looks like it disagrees with the sources badge.
     */
    @Test
    void supplierAndWarehouseTilesOpenThePagesBehindTheirFigures() {
        // given
        InventorySourcesView sources = new InventorySourcesView(List.of(), List.of(working("AB")), 2, 3, false);

        // when
        String summary = engine.process(SUMMARY, context(sources, true));
        Context warehouse = context(sources, true);
        warehouse.setVariable("externalWarehouse", false);
        warehouse.setVariable("warehouseSummary", new StockSummary(16, 52, 0));
        String tile = engine.process(WAREHOUSE, warehouse);

        // then
        assertThat(summary).contains("cl-stat-link").contains("connections enabled");
        assertThat(tile).contains("cl-stat-link").contains("href=\"/dashboard/warehouse\"");
        assertThat(summary + tile).doesNotContain("??");
    }

    /** A source that looks wrong is worth opening; the row is the shortest way to its settings. */
    @Test
    void aSourceRowLinksToItsOwnConnectionForAdminOnly() {
        // given
        InventorySourcesView sources = new InventorySourcesView(List.of(), List.of(working("Elko")), 2, 3, false);

        // when
        String admin = engine.process(SUMMARY, context(sources, true));
        String user = engine.process(SUMMARY, context(sources, false));

        // then
        assertThat(admin).contains("href=\"/dashboard/store/suppliers/Elko\"").contains("cl-inv-source-link");
        assertThat(user).doesNotContain("cl-inv-source-link").contains("Elko");
    }

    @Test
    void hidesTheManageShortcutFromUsersWhoCannotManageSuppliers() {
        // given
        InventorySourcesView sources = new InventorySourcesView(List.of(), List.of(working("AB")), 1, 1, false);

        // when
        String html = engine.process(SUMMARY, context(sources, false));

        // then
        assertThat(html).doesNotContain("/dashboard/store/suppliers");
    }

    @Test
    void collapsedBarNamesTwoIssuesAndCountsTheRest() {
        // given
        InventorySourcesView sources = new InventorySourcesView(
                List.of(missingFile("Hurtownia Nowak"), missingFile("Kosatec"), missingFile("Zeta")),
                List.of(working("AB")), 4, 4, false);

        // when
        String html = engine.process(SUMMARY, context(sources, true));

        // then
        assertThat(html).contains("Needs attention: 3");
        assertThat(html).contains("Hurtownia Nowak").contains("Kosatec").contains("+1");
        assertThat(html).contains("Need attention").contains("no feed file");
        assertThat(html).doesNotContain("All sources have a feed file");
    }

    @Test
    void warehouseTileShowsCountsOrTheExternalWarehouseLink() {
        // given
        Context counted = context(InventorySourcesView.EMPTY, true);
        counted.setVariable("externalWarehouse", false);
        counted.setVariable("warehouseSummary", new StockSummary(184, 612, 2));
        Context external = context(InventorySourcesView.EMPTY, true);
        external.setVariable("externalWarehouse", true);
        external.setVariable("warehouseSummary", StockSummary.EMPTY);

        // when
        String countedHtml = engine.process(WAREHOUSE, counted);
        String externalHtml = engine.process(WAREHOUSE, external);

        // then
        assertThat(countedHtml).contains("data-inventory-fragment=\"warehouse\"").contains("184").contains("612 pcs in stock").contains("+2 in transit");
        assertThat(countedHtml).containsPattern("data-warehouse-products-label[^>]*>Products: 184<");
        assertThat(externalHtml).contains("External warehouse").contains("href=\"/dashboard/warehouse\"")
                .doesNotContain("data-warehouse-products-label");
        assertThat(countedHtml + externalHtml).doesNotContain("??");
    }

    @Test
    void externalWarehouseRowHasNoProductCountPlaceholder() {
        // given
        InventorySourcesView sources = new InventorySourcesView(List.of(), List.of(working("AB")), 1, 1, true);

        // when
        String html = engine.process(SUMMARY, context(sources, true));

        // then
        assertThat(html).contains("External warehouse").doesNotContain("data-inventory-warehouse-products");
        assertThat(html).contains("Sources: 2");
    }

    @Test
    void warehouseTileHidesTheInTransitNoteWhenNothingIsInDelivery() {
        // given
        Context noneInDelivery = context(InventorySourcesView.EMPTY, true);
        noneInDelivery.setVariable("externalWarehouse", false);
        noneInDelivery.setVariable("warehouseSummary", new StockSummary(184, 612, 0));

        // when
        String html = engine.process(WAREHOUSE, noneInDelivery);

        // then
        assertThat(html).contains("612 pcs in stock").doesNotContain("in transit");
    }
}
