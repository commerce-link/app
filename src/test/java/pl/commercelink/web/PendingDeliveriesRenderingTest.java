package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesPageModel;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesPageModel.*;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;
import pl.commercelink.web.deliveries.pending.PendingDeliveryRow;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesRenderingTest {

    private static final String FRAGMENT = "<div th:replace=\"~{deliveries/pending :: results}\"></div>";
    private static final String PATH = "/dashboard/deliveries/preview";

    private static PendingDeliveryRow warehouseRow(boolean forward) {
        return new PendingDeliveryRow(Kind.WAREHOUSE, "AcmeB", null, null, "AcmeB", "AcmeB",
                "Zamówienia: 4 + uzupełnienie magazynu", forward, 8, "8 szt.", LocalDate.of(2026, 9, 30), "dziś", "is-warn",
                true, 1975.0, "1 975,00 PLN", "/dashboard/deliveries/create/AcmeB",
                "pending-w-AcmeB-1", List.of(new PendingDeliveryRow.Item("Samsung MirageDrive 2TB NVMe",
                        "MFN MFN-MIRAGE-01 · EAN 5900000000006", "3 szt.", "635,00 PLN", "1 905,00 PLN",
                        List.of(new PendingDeliveryRow.Source("5a7c3e10", "/dashboard/orders/5a7c3e10-x", 1, false),
                                new PendingDeliveryRow.Source("Magazyn", "/dashboard/warehouse/items/w-1", 2, true)))),
                List.of("5a7c3e10-x"), "");
    }

    private static PendingDeliveryRow dropshipRow() {
        return new PendingDeliveryRow(Kind.DROPSHIP, "1de57483", "/dashboard/orders/1de57483-x", "Barbara Zając", "AcmeB",
                "AcmeB", null, false, 1, "1 szt.", LocalDate.of(2026, 9, 28), "po terminie: 2 dni", "is-bad", false,
                635.0, "635,00 PLN", "/dashboard/deliveries/create/AcmeB?order=1de57483-x", "pending-d-1",
                List.of(new PendingDeliveryRow.Item("Samsung MirageDrive 2TB NVMe", "MFN MFN-MIRAGE-01 · EAN 5900000000006",
                        "1 szt.", "635,00 PLN", "635,00 PLN", List.of())), List.of("1de57483-x"), "");
    }

    private static PendingDeliveriesPageModel model(Kind active, List<PendingDeliveryRow> rows, EmptyState empty,
                                                    boolean nothing, List<Chip> chips) {
        PendingDeliveriesQuery query = new PendingDeliveriesQuery(null, List.of(), null);
        return new PendingDeliveriesPageModel(query, false, PATH, PATH + "/fragment", "/dashboard/deliveries",
                List.of(new KindTab("Do magazynu", 4, PATH + "?kind=warehouse", active == Kind.WAREHOUSE),
                        new KindTab("Dropshipping", 8, PATH + "?kind=dropship", active == Kind.DROPSHIP)),
                active, active == Kind.WAREHOUSE ? "Do magazynu" : "Dropshipping", "Opis zakładki.",
                List.of(new Option("AcmeB", "AcmeB", 2, false, PATH + "?provider=AcmeB")), "Wszyscy", chips, PATH, PATH,
                "Wyniki: " + rows.size(), rows, empty, nothing, chips.size());
    }

    private static String fragment(PendingDeliveriesPageModel page) {
        return SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));
    }

    @Test
    void warehouseTabRendersTheRowItsDetailAndItsSources() {
        // when
        String html = fragment(model(Kind.WAREHOUSE, List.of(warehouseRow(true)), null, false, List.of()));

        // then
        assertThat(html).contains("cl-table is-pending").contains(">AcmeB<").contains("Do dosłania klientowi")
                .contains("aria-controls=\"pending-w-AcmeB-1\"").contains("id=\"pending-w-AcmeB-1\"")
                .contains("/dashboard/deliveries/create/AcmeB").contains("5a7c3e10").contains("×2")
                .contains("/dashboard/warehouse/items/w-1").contains("aria-current=\"page\"").doesNotContain("??");
        // without JavaScript the detail row is visible and the toggle button hidden (row-toggle.js swaps them)
        assertThat(html).doesNotContainPattern("<tr[^>]*class=\"cl-row-detail\"[^>]*hidden")
                .containsPattern("<button[^>]*data-cl-row-toggle[^>]*hidden");
    }

    @Test
    void approvalRowsCarryThePillAndOthersDoNot() {
        // when
        String warehouse = fragment(model(Kind.WAREHOUSE, List.of(warehouseRow(true)), null, false, List.of()));
        String dropship = fragment(model(Kind.DROPSHIP, List.of(dropshipRow()), null, false, List.of()));

        // then
        assertThat(warehouse).contains("cl-status is-info\">Z akceptacją</span>").contains("Do dosłania klientowi");
        assertThat(warehouse.indexOf("Do dosłania klientowi")).isLessThan(warehouse.indexOf("cl-status is-info"));
        assertThat(dropship).doesNotContain("cl-status is-info");
    }

    @Test
    void approvalPillFollowsTheOrderNumberInTheDropshipTab() {
        // given
        PendingDeliveryRow approval = new PendingDeliveryRow(Kind.DROPSHIP, "1de57483", "/dashboard/orders/1de57483-x", null,
                "Global", "Global", null, false, 1, "1 szt.", null, "brak terminu", "is-none", true, 10.0, "10,00 PLN",
                "/dashboard/deliveries/create/Global?order=1de57483-x", "pending-d-2", List.of(), List.of("1de57483-x"), "");

        // when
        String html = fragment(model(Kind.DROPSHIP, List.of(approval), null, false, List.of()));

        // then
        assertThat(html).containsPattern("<span class=\"cl-card-key\"><a class=\"cl-row-number\"[^>]*>1de57483</a><span class=\"cl-status is-info\">Z akceptacją</span></span>");
    }

    @Test
    void noOrderingModeColumnAndNoTiles() {
        // when
        String warehouse = fragment(model(Kind.WAREHOUSE, List.of(warehouseRow(false)), null, false, List.of()));
        String dropship = fragment(model(Kind.DROPSHIP, List.of(dropshipRow()), null, false, List.of()));

        // then
        for (String html : List.of(warehouse, dropship)) {
            assertThat(html).doesNotContain("Zamawianie").doesNotContain("Przez API").doesNotContain("Ręcznie")
                    .doesNotContain("is-static").contains("colspan=\"6\"");
            assertThat(html).doesNotContain("cl-stat-").doesNotContain("cl-stat ");
        }
    }

    @Test
    void dropshipTabLinksTheOrderAndItsDropshipPage() {
        // when
        String html = fragment(model(Kind.DROPSHIP, List.of(dropshipRow()), null, false, List.of()));

        // then
        assertThat(html).contains(">1de57483<").contains("/dashboard/orders/1de57483-x\"").contains("Barbara Zając")
                .contains("/dashboard/deliveries/create/AcmeB?order=1de57483-x").contains("po terminie: 2 dni")
                .contains(">Zamówienie<").doesNotContain(">Źródło<").doesNotContain("??");
    }

    @Test
    void emptyStatesRenderInsteadOfTheTable() {
        // when
        String nothing = fragment(model(Kind.WAREHOUSE, List.of(),
                new EmptyState("Nic nie czeka na zamówienie.", "Przejdź do listy dostaw", "/dashboard/deliveries", false), true, List.of()));
        String inline = fragment(model(Kind.DROPSHIP, List.of(),
                new EmptyState("Żadne zamówienie nie czeka na dropshipping.", null, null, true), false, List.of()));

        // then
        assertThat(nothing).contains("cl-list-empty").contains("Przejdź do listy dostaw")
                .doesNotContain("cl-segmented").doesNotContain("cl-table is-pending");
        assertThat(inline).contains("cl-table-section-empty").contains("cl-segmented").doesNotContain("cl-table is-pending");
    }

    @Test
    void theWholePageHasOneHeadingOneAndABackLink() {
        // when
        String html = SettingsTemplateRenderer.render("deliveries/pending",
                Map.of("page", model(Kind.WAREHOUSE, List.of(warehouseRow(false)), null, false,
                        List.of(new Chip("Po terminie", PATH, "Usuń filtr Po terminie")))));

        // then
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("Oczekujące dostawy").contains("class=\"cl-back\"").contains("href=\"/dashboard/deliveries\"")
                .contains("Wyczyść filtry").contains("Usuń filtr Po terminie").doesNotContain("??");
    }

    @Test
    void supplierMenuSitsInTheSecondRowNextToTheClearLinkOnlyWithChips() {
        // given
        List<Chip> chips = List.of(new Chip("Po terminie", PATH, "Usuń filtr Po terminie"));

        // when
        String withChips = fragment(model(Kind.WAREHOUSE, List.of(warehouseRow(false)), null, false, chips));
        String withoutChips = fragment(model(Kind.WAREHOUSE, List.of(warehouseRow(false)), null, false, List.of()));

        // then
        int rowStart = withChips.indexOf("class=\"cl-toolbar-row\"");
        int filters = withChips.indexOf("id=\"pending-filters\"");
        int menu = withChips.indexOf("data-cl-filter-menu=\"provider\"");
        int clear = withChips.indexOf("cl-toolbar-clear");
        int meta = withChips.indexOf("cl-list-meta");
        assertThat(withChips).contains("cl-table-toolbar is-stacked").doesNotContain("is-single-row")
                .contains("data-cl-toolbar-toggle").contains("aria-controls=\"pending-filters\"");
        assertThat(withChips.indexOf("cl-segmented")).isBetween(rowStart, filters);
        assertThat(withChips.indexOf("data-cl-toolbar-toggle")).isBetween(rowStart, filters);
        assertThat(filters).isLessThan(menu);
        assertThat(menu).isLessThan(clear);
        assertThat(clear).isLessThan(meta);
        assertThat(withoutChips).doesNotContain("cl-toolbar-clear");
    }
}
