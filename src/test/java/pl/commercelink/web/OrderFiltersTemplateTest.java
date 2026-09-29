package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFiltersTemplateTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void fourFieldsTakeSeveralValuesWhileShippingDueStaysASelect() throws Exception {
        // given
        String html = read("src/main/resources/templates/orders/filters.html");

        // when / then
        assertThat(html).contains("th:fragment=\"multiValueField(fieldId, inputName, field, labelKey, optionValues, labelPrefix, picked)\"")
                .contains("details class=\"cl-filter-menu is-field\" data-cl-filter-field")
                .contains("FilterConditionLabels).summary(field, picked, #messages)")
                .contains("FilterConditionLabels).isPicked(picked, option)")
                .contains("multiValueField('filter-status', 'status', 'Status'")
                .contains("multiValueField('filter-shipment-type', 'shipmentType', 'ShipmentType'")
                .contains("multiValueField('filter-payment-source', 'paymentSource', 'PaymentSource'")
                .contains("multiValueField('filter-source-name', 'sourceName', 'SourceName'")
                .contains("<select class=\"cl-select\" id=\"filter-shipping-due\" name=\"shippingDue\">")
                .doesNotContain("name=\"status\" aria-describedby").doesNotContain("id=\"filter-source-name\" th:unless")
                .contains("orders.filters.marketplaces.empty");
    }

    @Test
    void conditionPillsShowOneFieldPerPill() throws Exception {
        // given
        String html = read("src/main/resources/templates/orders/filters.html");

        // when / then
        assertThat(html).contains("th:each=\"group : ${filter.conditionsByField}\"")
                .contains("FilterConditionLabels).pill(group.key, group.value, #messages)")
                .doesNotContain("th:each=\"c : ${filter.conditions}\"");
    }

    @Test
    void theFieldMenuHasItsStylesAndScript() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");
        String js = read("src/main/resources/static/js/order-filter-form.js");

        // when / then
        assertThat(css).contains(".cl-page .cl-filter-menu.is-field .cl-filter-menu-trigger");
        assertThat(js).contains("data-cl-filter-field").contains("Escape").contains("data-cl-any").contains("data-cl-selected");
    }
}
