package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the purchase-state parts of {@code deliveryDetails.html} for a delivery whose supplier order is still being
 * confirmed. The whole page cannot be rendered by the lightweight engine (its top form uses {@code th:field}, see
 * {@link DeliveryPaymentsSectionPageRenderTest}), so the test cuts out the parts that carry the purchase actions —
 * the refresh button, the panel under the header form and the purchase modals — and renders them for real.
 */
class DeliveryDetailsAwaitingSupplierTemplateTest {

    private static final String EXTERNAL_ORDER_ID = "ZA/IE-26/01615674";

    @Test
    void awaitingSupplierConfirmationShowsInfoBannerWithoutActions() throws Exception {
        // given
        Delivery delivery = dispatchedDelivery(true);

        // when
        String html = renderPurchaseParts(delivery);

        // then
        assertThat(html).contains("notification is-info")
                .contains(EXTERNAL_ORDER_ID)
                .contains("Czekamy na potwierdzenie rezerwacji")
                .doesNotContain("notification is-warning")
                .doesNotContain("purchase/reconcile")
                .doesNotContain("purchase/force")
                .doesNotContain("purchase/complete")
                .doesNotContain("refresh-order-id");
    }

    @Test
    void dispatchedDeliveryWithoutTheFlagKeepsTheWarningAndItsActions() throws Exception {
        // given
        Delivery delivery = dispatchedDelivery(false);

        // when
        String html = renderPurchaseParts(delivery);

        // then
        assertThat(html).contains("notification is-warning")
                .doesNotContain("notification is-info")
                .contains("purchase/reconcile")
                .contains("purchase/force")
                .contains("purchase/complete")
                .contains("refresh-order-id");
    }

    private static Delivery dispatchedDelivery(boolean awaitingSupplierConfirmation) {
        Delivery delivery = new Delivery("store-1", null, "Action");
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.setExternalDeliveryId(EXTERNAL_ORDER_ID);
        delivery.setExternalDeliveryIdProvisional(true);
        delivery.setAwaitingSupplierConfirmation(awaitingSupplierConfirmation);
        return delivery;
    }

    private static String renderPurchaseParts(Delivery delivery) throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/deliveryDetails.html"));
        String refreshButton = slice(template, "<button type=\"submit\" form=\"refresh-order-id-form\"", "</button>")
                + "</button>";
        // the panel slice ends with the closing tag of its column, so it gets an opening one
        String panel = "<div>" + slice(template, "<form id=\"refresh-order-id-form\"", "<div class=\"column is-half\">");
        String modals = slice(template, "<div id=\"completePurchaseModal\"", "<div id=\"confirmActionModal\"");
        Map<String, Object> variables = Map.of("delivery", delivery, "isAdmin", true, "isSuperAdmin", false);
        // the string resolver only takes single-line markup; newlines in the cut-outs are plain whitespace
        String markup = ("<div>" + refreshButton + panel + modals + "</div>").replace('\n', ' ');
        return SettingsTemplateRenderer.render(markup, variables);
    }

    private static String slice(String template, String from, String to) {
        int start = template.indexOf(from);
        assertThat(start).as("template contains %s", from).isNotNegative();
        int end = template.indexOf(to, start);
        assertThat(end).as("template contains %s after %s", to, from).isPositive();
        return template.substring(start, end);
    }
}
