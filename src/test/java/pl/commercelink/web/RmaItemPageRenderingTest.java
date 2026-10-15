package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.RmaItemForm;
import pl.commercelink.web.orders.OrderLinks;
import pl.commercelink.web.rma.RmaItemPage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaItemPageRenderingTest {

    private static RmaItemForm form() {
        RmaItemForm form = new RmaItemForm();
        form.setSerialNo("A1");
        form.setReason("Artefacts");
        form.setComment("Box ok");
        form.setVersion("5");
        return form;
    }

    private static RmaItemPage page(boolean readOnly, Map<String, String> errors, String errorMessage, boolean warn) {
        return new RmaItemPage("rma-1", "item-1", "/dashboard/rma/rma-1", "RMA rma-1",
                "RTX 4070", "Received", "is-warn", "1 pcs", "4711377139183", "V513-610R", "item-1",
                readOnly, readOnly ? "RMA completed" : null, form(), errors, errorMessage,
                new RmaItemPage.SerialHint(warn ? "Number X9 is not on the order item" : "Separate with commas. In the order: A1.", warn),
                new RmaItemPage.Resolution("Replacement", null),
                List.of(new RmaItemPage.Fact("Quantity", "1 pcs")), "2 899,00 PLN",
                new RmaItemPage.Context("/dashboard/rma/rma-1", "rma-1", "Items received", "is-warn", "Store", "Jan Kowalski · Poznań",
                        "/dashboard/orders/order-1", "order-1", null, "5a7c3e10", null,
                        List.of(new OrderLinks.SerialHistory("A1", "/dashboard/item/history?serialNo=A1"))),
                null);
    }

    private static String render(String fragment, RmaItemPage page) {
        Context context = new Context();
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{rma-item :: " + fragment + "}\"></div>", context);
    }

    @Test
    void openRmaShowsTheAsyncFormWithVersionThreeFieldsAndFactCards() {
        // when
        String html = render("body", page(false, Map.of(), null, false));

        // then
        assertThat(html).doesNotContain("??").doesNotContain("\"button is-primary").doesNotContain("style=");
        assertThat(html).contains("id=\"rma-item-form\"").contains("data-cl-async").contains("name=\"version\" value=\"5\"");
        assertThat(html).contains("name=\"serialNo\"").contains("name=\"reason\"").contains("name=\"comment\"");
        assertThat(html).contains("RTX 4070").contains("cl-status is-warn").contains("Not decided yet");
        assertThat(html).contains("href=\"/dashboard/orders/order-1\"").contains("href=\"/dashboard/item/history?serialNo=A1\"");
        assertThat(html).contains("The customer sees it");
        assertThat(html).contains("action=\"/dashboard/rma/rma-1/items/item-1/save\"");
        assertThat(html).contains("cl-form-grid cl-card-grid is-last");
    }

    @Test
    void closedRmaHasNoFormAndShowsTheValuesReadOnly() {
        // when
        String html = render("body", page(true, Map.of(), null, false));

        // then
        assertThat(html).doesNotContain("<form").doesNotContain("??");
        assertThat(html).contains("RMA completed").contains("cl-kv is-stacked").contains("Artefacts");
        assertThat(html).contains("id=\"rma-item-context-title\"").contains("href=\"/dashboard/orders/order-1\"").contains("Not decided yet");
    }

    @Test
    void errorsAndConflictRenderInsideTheFormFragment() {
        // when
        String html = render("itemForm", page(false, Map.of("reason", "rma.item.page.error.reason"), "Changed meanwhile", false));

        // then
        assertThat(html).startsWith("<form").doesNotContain("??");
        assertThat(html).contains("Changed meanwhile").contains("reason-error").contains("aria-invalid=\"true\"");
        assertThat(html).contains("data-cl-error-summary");
    }

    @Test
    void serialWarningUsesTheWarningHelpTone() {
        // when
        String html = render("itemForm", page(false, Map.of(), null, true));

        // then
        assertThat(html).contains("cl-help is-break is-warn").contains("Number X9 is not on the order item");
    }

    @Test
    void rmaStatusHasItsOwnRowUnderTheRmaLink() {
        // when
        String html = render("body", page(false, Map.of(), null, false));

        // then
        assertThat(html).containsPattern("<dt>RMA</dt><dd><a href=\"/dashboard/rma/rma-1\">rma-1</a></dd>")
                .containsPattern("<dt>RMA status</dt>\\s*<dd><span class=\"cl-status is-warn\">Items received</span></dd>");
    }
}
