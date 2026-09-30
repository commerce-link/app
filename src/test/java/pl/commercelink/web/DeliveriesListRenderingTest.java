package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.deliveries.DeliveriesPageModel;
import pl.commercelink.web.deliveries.DeliveriesPageModel.*;
import pl.commercelink.web.deliveries.DeliveryListQuery;
import pl.commercelink.web.deliveries.DeliveryRow;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the results fragment of the deliveries list with a real model and the Polish bundle. */
class DeliveriesListRenderingTest {

    private static final String FRAGMENT = "<div th:replace=\"~{deliveries :: results}\"></div>";

    @Test
    void rendersATransitRowAndAReceivedRowWithoutInvoice() {
        // given
        DeliveryListQuery query = new DeliveryListQuery(DeliveryListQuery.Scope.ALL, null, List.of(), List.of(), List.of(),
                null, null, false, null, null, null, 1);
        DeliveryRow transit = new DeliveryRow("/dashboard/deliveries/details?deliveryId=a1", "a1b2c3d4", null, false, "Acme",
                "ZS/1", false, "EuSarl", "24.09", "27.09", "deliveries.list.column.planned", "po terminie: 3 dni", "is-bad",
                "W drodze", "is-info", "4 157,40 PLN", "netto 3 380,00 PLN", List.of());
        DeliveryRow received = new DeliveryRow("/dashboard/deliveries/details?deliveryId=b2", "b2c3d4e5", null, true, "Action",
                null, false, null, "20.09", "25.09", "deliveries.list.column.received", null, "",
                "Odebrana", "is-ok", "798,27 PLN", "netto 649,00 PLN",
                List.of(new DeliveryRow.Mark("FV", false, "Faktura zakupowa: brak")));
        Map<DeliveryListQuery.Sort, SortHeader> headers = new java.util.EnumMap<>(DeliveryListQuery.Sort.class);
        for (DeliveryListQuery.Sort sort : DeliveryListQuery.Sort.values()) {
            headers.put(sort, new SortHeader("/dashboard/deliveries?sort=" + sort.param(), sort == DeliveryListQuery.Sort.DUE ? "ascending" : "none"));
        }
        DeliveriesPageModel page = new DeliveriesPageModel(query, false, true,
                List.of(new Tile("Po terminie", 1, "planowana dostawa minęła", "/dashboard/deliveries?focus=overdue", true)),
                List.of(new ScopeOption("Wszystkie", null, "/dashboard/deliveries?scope=all", true)),
                List.of(new Option("inTransit", "W drodze", 1, false, "/dashboard/deliveries?state=inTransit"),
                        new Option("received", "Odebrana", 1, false, "/dashboard/deliveries?state=received")), "wszystkie",
                List.of(new Option("Acme", "Acme", 1, false, "/dashboard/deliveries?provider=Acme")), "wszystkie",
                List.of(new Option("noInvoice", "Bez faktury", 1, false, "/dashboard/deliveries?settle=noInvoice")), "wszystkie",
                new DateMenu("Odebrana", "od początku", null, null, "/dashboard/deliveries?period=all", false),
                List.of(), "Dostawy: 2", headers, List.of(transit, received),
                Pagination.of(1, 2, 25, n -> "/dashboard/deliveries?page=" + n), null, 0);

        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));

        // then
        assertThat(html).contains("cl-status is-info").contains("cl-status is-ok").contains("cl-doc-mark is-todo")
                .contains("po terminie").contains("aria-current=\"true\"").contains("nr ZS/1").contains("kontrahent EuSarl")
                .contains("Dropshipping").contains("Magazyn").doesNotContain("??");
    }
}
