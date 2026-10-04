package pl.commercelink.web;

import org.junit.jupiter.api.Test;
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
 * The add-items and add-payment dialogs are shared with Offers, Payments and Deliveries (Bulma pages).
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
    void theDialogScriptGuardsFormSubmitsAndReopensServerOpenedDialogsAsModal() throws Exception {
        // given
        String script = read("src/main/resources/static/js/dialog.js");

        // then
        assertThat(script).contains("addEventListener('submit'").contains("event.submitter").contains("button.disabled = true")
                .contains("event.defaultPrevented").contains("data-cl-async").contains("dialog.cl-dialog.is-form")
                .contains("'pageshow'").contains("shown.persisted")
                .contains("dialog.cl-dialog[open]").contains("if (!dialog.open)");
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

    /** PaymentsCard.sources is a precomputed Option<PaymentSource> list; templates read labelKey, never build the key themselves. */
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
    void theAddPaymentAmountsAreTextTheFocusStartsOnTheAmountAndTheHelpFollowsTheMode() {
        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/add-payment-modal :: addPaymentModal('', 0, ${null}, ${paymentSources}, true)}\"></div>",
                paymentVariables());

        // then: a number field let WebKit with en-US read "149,99" as 14999
        assertThat(html).containsPattern("<input class=\"cl-input\" type=\"text\" inputmode=\"decimal\" autocomplete=\"off\" required autofocus\\s+id=\"addPaymentBankAmount\"")
                .containsPattern("type=\"text\" inputmode=\"decimal\" autocomplete=\"off\" id=\"addPaymentProcessingFee\"")
                .containsPattern("type=\"text\" readonly id=\"addPaymentExpected\"")
                .doesNotContain("type=\"number\"").doesNotContain("step=")
                .contains("data-help-order=\"Przychodząca = wpłata od klienta. Wychodząca = zwrot do klienta. Kwotę wpisz bez znaku.\"")
                .contains("data-help-delivery=\"Przychodząca = wpłata od klienta / od dystrybutora.");
    }

    @Test
    void theAddPaymentDialogSkipsMoneyJsOnlyWhenToldTo() {
        // given: the order details page includes this fragment next to fragments/item-add-modal.html,
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
    void theAddPaymentScriptKeepsEveryPublicFunctionOfPaymentsAndDeliveries() throws Exception {
        // given
        String script = read("src/main/resources/static/js/add-payment-dialog.js");
        String payments = read("src/main/resources/templates/payments.html");

        // when / then
        assertThat(script).contains("window.toggleAddPaymentModal = toggleAddPaymentModal")
                .contains("window.openPaymentModalForOrder = openPaymentModalForOrder")
                .contains("window.openPaymentModalForOrderFromButton = openPaymentModalForOrderFromButton")
                .contains("window.openPaymentModalForDelivery = openPaymentModalForDelivery")
                .contains("window.openPaymentModalForDeliveryFromButton = openPaymentModalForDeliveryFromButton")
                .contains("showModal").contains("cl:dialog-open").contains("'use strict'")
                .doesNotContain("innerHTML").doesNotContain(".style.").doesNotContain("pełna wpłata")
                .doesNotContain("togglePaymentsEditModal").doesNotContain("openAddPaymentModalFromButton");
        // Payments opens the dialog from links the script finds by delegation, so the block list-page.js swaps keeps working
        assertThat(payments).contains("data-cl-payment-open").contains("data-order-id=${row.orderId()}")
                .contains("data-delivery-id=${row.deliveryId()}");
        assertThat(script).contains("closest('[data-cl-payment-open]')");
        // the fragment lists the directions by hand (no T() in templates); a third direction must be added there too
        assertThat(PaymentDirection.values()).containsExactly(PaymentDirection.Incoming, PaymentDirection.Outgoing);
    }
}
