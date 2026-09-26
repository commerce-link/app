package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review Focus 5: the add-items and add-payment dialogs are shared with Offers, Payments and Deliveries (Bulma pages).
 * They keep their public JavaScript functions and bring their own .cl-page wrapper and script.
 */
class SharedDialogsContractTest {

    static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void theAddItemsDialogRendersStandaloneWithItsOwnWrapperAndScript() {
        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/item-add-modal :: itemAddModal('basket-1', '/dashboard/offer')}\"></div>",
                Map.of("catalogs", List.of()));

        // then
        assertThat(html).contains("class=\"cl-page cl-dialog-host\"")
                .containsPattern("<dialog class=\"cl-dialog is-form is-wide\" id=\"item-add-dialog\"")
                .contains("data-add-items-url=\"/dashboard/offer/basket-1/add-items\"")
                .contains("/js/item-add-dialog.js").contains("data-cl-item-add-close")
                .doesNotContain("onclick=").doesNotContain("oninput=").doesNotContain("onchange=")
                .doesNotContain("<style").doesNotContain("style=").doesNotContain("modal-card");
    }

    @Test
    void theAddItemsScriptKeepsThePublicFunctionOfTheOfferPage() throws Exception {
        // given
        String script = read("src/main/resources/static/js/item-add-dialog.js");
        String offer = read("src/main/resources/templates/offerDetails.html");

        // then
        assertThat(script).contains("window.toggleAddItemModal = toggleAddItemModal").contains("showModal")
                .contains("cl:dialog-open").contains("items[").contains("data-add-items-url").contains("'use strict'")
                .doesNotContain("innerHTML").doesNotContain(".style.");
        assertThat(offer).contains("fragments/item-add-modal :: itemAddModal(").contains("toggleAddItemModal(");
    }

    /** T9b turned PaymentsCard.sources into a precomputed Option<PaymentSource> list; templates read labelKey, never build the key themselves. */
    static Map<String, Object> paymentVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentSources", OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource));
        return variables;
    }

    @Test
    void theAddPaymentDialogRendersStandaloneWithItsOwnWrapperScriptAndWords() {
        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/add-payment-modal :: addPaymentModal('', 0, ${null}, ${paymentSources}, ${null})}\"></div>",
                paymentVariables());

        // then
        assertThat(html).contains("class=\"cl-page cl-dialog-host\"")
                .containsPattern("<dialog class=\"cl-dialog is-form\" id=\"addPaymentModal\"")
                .contains("/js/money.js").contains("/js/add-payment-dialog.js").contains("data-cl-payment-close")
                .contains(">Przelew bankowy</option>").contains(">Przychodząca</option>").contains(">Wychodząca</option>")
                .contains("data-hint-under=\"niedopłata {0}\"")
                .doesNotContain(">BankTransfer<").doesNotContain("onclick=").doesNotContain("oninput=")
                .doesNotContain("onchange=").doesNotContain("style=").doesNotContain("<style")
                .doesNotContain("modal-card").doesNotContain("<script>");
    }

    @Test
    void theAddPaymentDialogSkipsMoneyJsOnlyWhenToldTo() {
        // given (Task 24 hygiene): the order details page includes this fragment next to fragments/item-add-modal.html,
        // which already loads money.js -- loading it twice would run its IIFE twice, so that one caller passes
        // loadMoneyJs=false. Thymeleaf requires every caller to pass the parameter; every other caller passes
        // true explicitly and keeps getting it, pinned by the test above.
        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/add-payment-modal :: addPaymentModal('', 0, ${null}, ${paymentSources}, false)}\"></div>",
                paymentVariables());

        // then
        assertThat(html).doesNotContain("money.js").contains("/js/add-payment-dialog.js");
    }

    @Test
    void theDeliveryPaymentsSectionKeepsItsBulmaEditModalAndGetsTheNewDialog() {
        // given
        Map<String, Object> variables = paymentVariables();
        variables.put("payments", List.of(new Payment("REF-1", "Jan", PaymentSource.BankTransfer, 50, 0)));

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/payments-section :: paymentsSection(${payments}, '/dashboard/deliveries/d-1/addPayment',"
                        + " '/dashboard/deliveries/d-1/updatePayments', 50, ${null}, ${paymentSources}, false, true, 'delivery')}\"></div>",
                variables);

        // then
        assertThat(html).contains("id=\"paymentsEditModal\"").contains("togglePaymentsEditModal(true)")
                .contains("openAddPaymentModalFromButton(this)").contains("id=\"addPaymentModal\"")
                .contains("action=\"/dashboard/deliveries/d-1/addPayment\"");
        // the edit modal's own <select> (payments-section.html) must read the same Option value/labelKey the add
        // dialog does, not Option's own toString() (Critical 1, task-17 review round 1)
        assertThat(html).doesNotContain("Option[")
                .containsPattern("<option value=\"BankTransfer\"[^>]*selected[^>]*>Przelew bankowy<");
    }

    @Test
    void theAddPaymentScriptKeepsEveryPublicFunctionOfPaymentsAndDeliveries() throws Exception {
        // given
        String script = read("src/main/resources/static/js/add-payment-dialog.js");
        String payments = read("src/main/resources/templates/payments.html");

        // then
        assertThat(script).contains("window.toggleAddPaymentModal = toggleAddPaymentModal")
                .contains("window.togglePaymentsEditModal = togglePaymentsEditModal")
                .contains("window.openAddPaymentModalFromButton = openAddPaymentModalFromButton")
                .contains("window.openPaymentModalForOrder = openPaymentModalForOrder")
                .contains("window.openPaymentModalForOrderFromButton = openPaymentModalForOrderFromButton")
                .contains("window.openPaymentModalForDelivery = openPaymentModalForDelivery")
                .contains("window.openPaymentModalForDeliveryFromButton = openPaymentModalForDeliveryFromButton")
                .contains("showModal").contains("cl:dialog-open").contains("'use strict'")
                .doesNotContain("innerHTML").doesNotContain(".style.").doesNotContain("pełna wpłata");
        assertThat(payments).contains("openPaymentModalForOrderFromButton(this)").contains("openPaymentModalForDeliveryFromButton(this)");
        // the fragment lists the directions by hand (no T() in templates); a third direction must be added there too
        assertThat(PaymentDirection.values()).containsExactly(PaymentDirection.Incoming, PaymentDirection.Outgoing);
    }
}
