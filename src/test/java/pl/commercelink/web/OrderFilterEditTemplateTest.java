package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.OrderListService;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.filters.CustomerType;
import pl.commercelink.orders.filters.ShippingDue;
import pl.commercelink.web.dtos.OrderFilterForm;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilterEditTemplateTest {

    private static String render(OrderFilterForm form, List<String> connectedMarketplaces) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("returnTo", "/dashboard/orders/filters");
        variables.put("pageTitle", "Edytuj filtr");
        variables.put("formAction", "/dashboard/orders/filters/update");
        variables.put("filterForm", form);
        variables.put("filterId", "f1");
        variables.put("statuses", OrderListService.OPEN);
        variables.put("shipmentTypes", ShipmentType.values());
        variables.put("paymentSources", PaymentSource.values());
        variables.put("shippingDueOptions", ShippingDue.values());
        variables.put("customerTypeOptions", CustomerType.values());
        variables.put("marketplaces", connectedMarketplaces);
        variables.put("canManageStoreFilters", false);
        return SettingsTemplateRenderer.render("orders/filter-edit", variables);
    }

    private static OrderFilterForm form(List<String> statuses, List<String> marketplaces) {
        OrderFilterForm form = new OrderFilterForm();
        form.setLabel("Allegro i Morele");
        form.setStatus(statuses);
        form.setSourceName(marketplaces);
        return form;
    }

    @Test
    void aMarketplaceNoLongerConnectedStaysTickedSoSavingKeepsIt() {
        // when
        String html = render(form(List.of(), List.of("Allegro", "Morele")), List.of("Allegro"));

        // then
        assertThat(html).containsPattern("name=\"sourceName\" value=\"Allegro\" checked=\"checked\"")
                .containsPattern("name=\"sourceName\" value=\"Morele\" checked=\"checked\"")
                .contains("Wybrano: 2").doesNotContain("??");
    }

    @Test
    void storedMarketplacesAreStillOfferedWhenNoneIsConnected() {
        // when
        String html = render(form(List.of(), List.of("Morele")), List.of());

        // then
        assertThat(html).containsPattern("name=\"sourceName\" value=\"Morele\" checked=\"checked\"")
                .doesNotContain("Brak podłączonych");
    }

    @Test
    void aClosedStatusIsListedTickedWithItsLabelAndTheTriggerCountsBoth() {
        // when
        String html = render(form(List.of("New", "Completed", "Garbage"), List.of()), List.of());

        // then
        assertThat(html).containsPattern("name=\"status\" value=\"Completed\" checked=\"checked\"")
                .containsPattern("name=\"status\" value=\"Garbage\" checked=\"checked\"")
                .contains(">Zakończone<").contains(">Garbage<")
                .contains("Wybrano: 3").doesNotContain("??");
    }

    @Test
    void theSavedCustomerTypeIsSelected() {
        // given
        OrderFilterForm form = form(List.of(), List.of());
        form.setCustomerType("b2b");

        // when
        String html = render(form, List.of());

        // then
        assertThat(html).contains("<select class=\"cl-select\" id=\"filter-customer-type\" name=\"customerType\">")
                .containsPattern("<option value=\"B2B\" selected=\"selected\">Firma \\(B2B\\)</option>")
                .containsPattern("<option value=\"B2C\">Osoba prywatna \\(B2C\\)</option>")
                .contains(">Klient<").doesNotContain("??");
    }
}
