package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.deliveries.DeliveriesPageModel;
import pl.commercelink.web.deliveries.DeliveriesPageModel.*;
import pl.commercelink.web.deliveries.DeliveryListQuery;
import pl.commercelink.web.deliveries.DeliveryListQuery.Scope;
import pl.commercelink.web.deliveries.DeliveryRow;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the deliveries template with a real model and the Polish bundle. */
class DeliveriesListRenderingTest {

    private static final String FRAGMENT = "<div th:replace=\"~{deliveries :: results}\"></div>";

    private static DeliveryListQuery query(Scope scope) {
        return new DeliveryListQuery(scope, null, List.of(), List.of(), List.of(), null, null, false, null, null, null, 1);
    }

    private static DeliveryRow transit(String storeId) {
        String base = storeId == null ? "/dashboard" : "/dashboard/store/" + storeId;
        return new DeliveryRow(base + "/deliveries/details?deliveryId=a1", "a1b2c3d4", storeId, false, "Acme",
                "ZS/1", false, "EuSarl", "24.09", "27.09", "deliveries.list.column.planned", "po terminie: 3 dni", "is-bad",
                "W drodze", "is-info", "4 157,40 PLN", "netto 3 380,00 PLN", List.of());
    }

    private static DeliveryRow received(String storeId) {
        String base = storeId == null ? "/dashboard" : "/dashboard/store/" + storeId;
        return new DeliveryRow(base + "/deliveries/details?deliveryId=b2", "b2c3d4e5", storeId, true, "Action",
                null, false, null, "20.09", "25.09", "deliveries.list.column.received", null, "",
                "Odebrana", "is-ok", "798,27 PLN", "netto 649,00 PLN",
                List.of(new DeliveryRow.Mark("FV", false, "Faktura zakupowa: brak")));
    }

    private static DeliveriesPageModel model(DeliveryListQuery query, boolean superAdmin, List<DeliveryRow> rows,
                                             List<Chip> chips, EmptyState emptyState) {
        Map<DeliveryListQuery.Sort, SortHeader> headers = new EnumMap<>(DeliveryListQuery.Sort.class);
        for (DeliveryListQuery.Sort sort : DeliveryListQuery.Sort.values()) {
            headers.put(sort, new SortHeader("/dashboard/deliveries?sort=" + sort.param(), sort == DeliveryListQuery.Sort.DUE ? "ascending" : "none"));
        }
        return new DeliveriesPageModel(query, superAdmin, true,
                List.of(new Tile("Po terminie", 1, "planowana dostawa minęła", "/dashboard/deliveries?focus=overdue", true)),
                List.of(new ScopeOption("Wszystkie", null, "/dashboard/deliveries?scope=all", true)),
                List.of(new Option("inTransit", "W drodze", 1, false, "/dashboard/deliveries?state=inTransit")),
                List.of(new Option("received", "Odebrana", 1, false, "/dashboard/deliveries?state=received")), "wszystkie",
                List.of(new Option("Acme", "Acme", 1, false, "/dashboard/deliveries?provider=Acme")), "wszystkie",
                List.of(new Option("noInvoice", "Bez faktury", 1, false, "/dashboard/deliveries?settle=noInvoice")), "wszystkie",
                new DateMenu("Odebrana", "od początku", null, null, "/dashboard/deliveries?period=all", false),
                chips, "Dostawy: " + rows.size(), headers, rows,
                Pagination.of(1, rows.size(), 25, n -> "/dashboard/deliveries?page=" + n), emptyState, chips.size());
    }

    private static String fragment(DeliveriesPageModel page) {
        return SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));
    }

    @Test
    void rendersATransitRowAndAReceivedRowWithoutInvoice() {
        // given
        DeliveriesPageModel page = model(query(Scope.ALL), false, List.of(transit(null), received(null)), List.of(), null);

        // when
        String html = fragment(page);

        // then
        assertThat(html).contains("cl-status is-info").contains("cl-status is-ok").contains("cl-doc-mark is-todo")
                .contains("po terminie").contains("aria-current=\"true\"").contains("nr ZS/1").contains("kontrahent EuSarl")
                .contains("Dropshipping").contains("Magazyn").contains("Nieodebrane").contains("Dostawy odebrane")
                .doesNotContain("Sklep").doesNotContain("??");
    }

    @Test
    void theWholePageRendersWithTheHelpersOutsideTheResultsFragment() {
        // given
        DeliveriesPageModel page = model(query(Scope.ALL), false, List.of(transit(null)), List.of(), null);

        // when
        String html = SettingsTemplateRenderer.render("deliveries", Map.of("page", page));

        // then
        assertThat(html).contains("<h1").contains("data-cl-list-results").contains("name=\"state\" value=\"inTransit\"")
                .contains("Utwórz dostawę").doesNotContain("??");
        // the helpers render only inside a menu's form, never as a stray field of the page
        assertThat(html.indexOf("<input type=\"hidden\"")).isGreaterThan(html.indexOf("<form"));
        assertThat(html.substring(html.indexOf("</section>", html.indexOf("list-page.js") - 400))).doesNotContain("type=\"hidden\"");
    }

    @Test
    void superAdminGetsTheStoreColumnAndStoreScopedLinks() {
        // given
        DeliveriesPageModel page = model(query(Scope.TRANSIT), true, List.of(transit("store-7")), List.of(), null);

        // when
        String html = fragment(page);

        // then
        assertThat(html).contains(">Sklep<").contains(">store-7<").contains("/dashboard/store/store-7/deliveries/details?deliveryId=a1")
                .doesNotContain("??");
    }

    @Test
    void theReceivedScopeNamesTheDateColumnAfterReception() {
        // given
        DeliveriesPageModel page = model(query(Scope.RECEIVED), false, List.of(received(null)), List.of(), null);

        // when
        String html = fragment(page);

        // then
        assertThat(html).contains(">Odebrana</a>").doesNotContain("Planowana dostawa").doesNotContain("??");
    }

    @Test
    void anEmptyStateShowsItsTextAndAction() {
        // given
        DeliveriesPageModel page = model(query(Scope.TRANSIT), false, List.of(), List.of(),
                new EmptyState("Nic nie jest w drodze.", "Odebrane", "/dashboard/deliveries?scope=received"));

        // when
        String html = fragment(page);

        // then
        assertThat(html).contains("cl-list-empty").contains("Nic nie jest w drodze.")
                .contains("href=\"/dashboard/deliveries?scope=received\"").doesNotContain("<table").doesNotContain("??");
    }

    @Test
    void chipsCarryTheirClearLinks() {
        // given
        DeliveriesPageModel page = model(query(Scope.TRANSIT), false, List.of(transit(null)),
                List.of(new Chip("Dostawca: Acme", "/dashboard/deliveries", "Wyczyść: Dostawca: Acme")), null);

        // when
        String html = fragment(page);

        // then
        assertThat(html).contains("cl-filter-chip").contains("Dostawca: Acme").contains("href=\"/dashboard/deliveries\"")
                .contains("aria-label=\"Wyczyść: Dostawca: Acme\"").contains("cl-toolbar-clear").doesNotContain("??");
    }
}
