package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.web.dtos.BankAccountForm;
import pl.commercelink.web.dtos.CheckoutSettingsForm;
import pl.commercelink.web.dtos.CurrencyOptions;
import pl.commercelink.web.dtos.DeliveryOptionForm;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.dtos.PickerOption;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorePaymentsTemplateTest {

    private static final String PATH = "/dashboard/store/payments";
    private static final String WEBHOOK = "https://api.commercelink.pl/Store/store-1/Webhooks/Payments/stripe";

    private static PaymentGatewayView gateway(String name, String displayName, boolean isDefault, boolean installed,
                                              boolean configured) {
        String base = PATH + "/gateways/" + name;
        return new PaymentGatewayView(name, displayName, isDefault, installed, configured, base, base + "/default",
                base + "/disconnect");
    }

    private static BankAccountView account(String bank, String iban, boolean isDefault) {
        BankAccount account = new BankAccount();
        account.setBankName(bank);
        account.setIban(iban);
        account.setAccountHolder("Sklep Demo sp. z o.o.");
        account.setCurrency("PLN");
        account.set_default(isDefault);
        return BankAccountView.of(account, PATH + "/bank-accounts");
    }

    private static DeliveryOptionView delivery(String name, double price, String description) {
        DeliveryOption option = new DeliveryOption();
        option.setName(name);
        option.setPrice(price);
        option.setDescription(description);
        option.setType(ShipmentType.Courier);
        return DeliveryOptionView.of(option, PATH + "/delivery-options");
    }

    private Map<String, Object> page(List<PaymentGatewayView> gateways, List<BankAccountView> accounts,
                                     List<DeliveryOptionView> options, boolean localhost) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("gateways", gateways);
        variables.put("gatewaysInstalled", true);
        variables.put("newGatewayHref", PATH + "/gateways/new");
        variables.put("gatewayDisconnectMessages", Map.of("stripe", "Klienci nie zapłacą już przez Stripe."));
        variables.put("accounts", accounts);
        variables.put("newAccountHref", PATH + "/bank-accounts/new");
        variables.put("deliveryOptions", options);
        variables.put("newDeliveryOptionHref", PATH + "/delivery-options/new");
        variables.put("checkoutForm", CheckoutSettingsForm.from(null));
        variables.put("checkoutErrors", Map.of());
        variables.put("checkoutAction", PATH);
        variables.put("currencies", List.of(new PickerOption("pln", "PLN · złoty polski")));
        variables.put("returnToLocalMachine", localhost);
        return variables;
    }

    private String rendered(List<PaymentGatewayView> gateways, List<BankAccountView> accounts,
                            List<DeliveryOptionView> options, boolean localhost) {
        return SettingsTemplateRenderer.render("store-payments", page(gateways, accounts, options, localhost));
    }

    private String typical() {
        return rendered(List.of(gateway("stripe", "Stripe", true, true, true), gateway("paynow", "PayNow", false, true, true)),
                List.of(account("Demo Bank", "PL61109010140000071219812874", true)),
                List.of(delivery("Kurier", 19.99, "1–2 dni")), false);
    }

    /** Lists first, one form last: "Save changes" appears once and saves only the checkout settings. */
    @Test
    void thePageHasThreeListsAndOneFormWithoutAStoreIdField() {
        // when
        String html = typical();

        // then
        assertThat(html.indexOf("payment-gateways-title")).isLessThan(html.indexOf("bank-accounts-title"));
        assertThat(html.indexOf("bank-accounts-title")).isLessThan(html.indexOf("delivery-options-title"));
        assertThat(html.indexOf("delivery-options-title")).isLessThan(html.indexOf("payments-checkout-form"));
        assertThat(html).containsOnlyOnce("data-cl-async");
        assertThat(html.split(">Zapisz zmiany<", -1)).hasSize(2);
        assertThat(html).doesNotContain("name=\"store.storeId\"").doesNotContain("name=\"storeId\"")
                .doesNotContain("onclick=\"confirmSave(this)\"").doesNotContain("table is-fullwidth");
    }

    @Test
    void gatewaysShowTheDefaultAndOfferToMakeAnotherOneDefault() {
        // when
        String html = typical();

        // then
        assertThat(html).containsOnlyOnce("<span class=\"cl-status is-info\">Domyślna</span>");
        assertThat(html).contains("aria-label=\"Ustaw jako domyślną: PayNow\"")
                .contains("action=\"/dashboard/store/payments/gateways/paynow/default\"");
        assertThat(html).contains("data-cl-confirm-message=\"Klienci nie zapłacą już przez Stripe.\"");
        assertThat(html).contains("href=\"/dashboard/store/payments/gateways/new\"").contains(">Dodaj bramkę<");
    }

    @Test
    void withoutAGatewayThePageSaysCustomersCannotPayOnline() {
        // when
        String html = rendered(List.of(), List.of(), List.of(), false);

        // then
        assertThat(html).contains("Klienci nie zapłacą online");
        assertThat(html).contains("Nie ma sposobów dostawy").contains("Nie masz jeszcze konta");
    }

    @Test
    void anIncompleteOrUninstalledGatewaySaysWhyItFails() {
        // when
        String html = rendered(List.of(gateway("stripe", "Stripe", true, true, false), gateway("gone", "gone", false, false, false)),
                List.of(), List.of(), false);

        // then
        assertThat(html).contains(">Niekompletna<").contains(">Uzupełnij<").contains("Brakuje danych dostępu");
        assertThat(html).contains(">Niedostępna<").contains("nie jest już zainstalowana");
        assertThat(html).doesNotContain("href=\"/dashboard/store/payments/gateways/gone\"")
                .doesNotContain("Ustaw jako domyślną: gone");
    }

    @Test
    void deliveryOptionsShowTypeAndPriceInZloty() {
        // when
        String html = typical();

        // then
        assertThat(html).contains(">Kurier<").contains("19,99 zł").contains("1–2 dni");
        assertThat(html).contains("data-cl-confirm-title=\"Usunąć Kurier?\"").contains("zachowają go z obecną ceną");
    }

    @Test
    void bankAccountsMovedHereWithTheirLinks() {
        // when
        String html = typical();

        // then
        assertThat(html).contains("PL61 1090 1014 0000 0712 1981 2874")
                .contains("href=\"/dashboard/store/payments/bank-accounts/new\"")
                .contains("To jedyne konto");
    }

    @Test
    void theCheckoutFormExplainsItsFieldsAndWarnsAboutLocalAddresses() {
        // when
        String warned = rendered(List.of(), List.of(), List.of(), true);
        String fine = typical();

        // then
        assertThat(warned).contains("komputer lokalny (localhost)");
        assertThat(fine).doesNotContain("komputer lokalny");
        assertThat(fine).contains("Paynow dopisuje na końcu numer zamówienia").contains("rozliczane w PLN")
                .contains("id=\"currency-help\"").contains("aria-describedby=\"currency-help\"");
        assertThat(fine).contains("/js/async-form.js").contains("/js/confirm-dialog.js");
    }

    @Test
    void theCheckoutFormRendersAloneForASaveWithoutReloading() {
        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-payments :: checkoutForm}\"></div>",
                page(List.of(), List.of(), List.of(), false));

        // then
        assertThat(html).contains("id=\"payments-checkout-form\"").doesNotContain("payment-gateways-title");
    }

    @Test
    void theGatewayPageShowsTheWebhookBeforeTheKeysWithTheStripeEvent() {
        // given
        PaymentProviderDescriptor stripe = mock(PaymentProviderDescriptor.class);
        when(stripe.name()).thenReturn("stripe");
        when(stripe.displayName()).thenReturn("Stripe");
        when(stripe.configurationFields()).thenReturn(List.of(
                new ProviderField("apiKey", "API Key", FieldType.PASSWORD, true, "")));
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", IntegrationSettingsForm.from("stripe", Map.of("apiKey", ""), stripe.configurationFields()));
        variables.put("errors", Map.of());
        variables.put("errorLabels", Map.of());
        variables.put("providers", List.of(stripe));
        variables.put("webhooks", Map.of("stripe", WEBHOOK));
        variables.put("editing", true);
        variables.put("providerKnown", true);
        variables.put("storedSecretIds", Set.of("setting-stripe-apiKey"));
        variables.put("alreadyDefault", true);
        variables.put("firstGateway", false);
        variables.put("makeDefault", true);
        variables.put("formAction", PATH + "/gateways/stripe");
        variables.put("paymentsHref", PATH);
        variables.put("backLabel", "Płatności");
        variables.put("pageTitle", "Bramka Stripe");

        // when
        String html = SettingsTemplateRenderer.render("store-payment-gateway", variables);

        // then
        assertThat(html).contains("data-value=\"" + WEBHOOK + "\"").contains("Adres powiadomień o płatności");
        assertThat(html).contains("<code class=\"cl-code\">charge.succeeded</code>");
        assertThat(html.indexOf(WEBHOOK)).isLessThan(html.indexOf("id=\"setting-stripe-apiKey\""));
        assertThat(html).doesNotContain("data-cl-variant-select").doesNotContain("name=\"providerName\"");
        assertThat(html).contains("To jest bramka domyślna").doesNotContain("name=\"makeDefault\"");
        assertThat(html).contains("Zapisany. Zostaw puste, żeby go nie zmieniać.").contains(">Zapisz bramkę<");
        assertThat(html).contains("/js/copy-field.js");
    }

    @Test
    void theDeliveryOptionPageAsksForNamePriceAndType() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", DeliveryOptionForm.empty());
        variables.put("errors", Map.of());
        variables.put("types", List.of(new PickerOption("Courier", "Kurier"), new PickerOption("PickupPoint", "Punkt odbioru")));
        variables.put("editing", false);
        variables.put("formAction", PATH + "/delivery-options/new");
        variables.put("pageTitle", "Nowy sposób dostawy");
        variables.put("paymentsHref", PATH);
        variables.put("backLabel", "Płatności");

        // when
        String html = SettingsTemplateRenderer.render("store-delivery-option", variables);

        // then
        assertThat(html).contains("action=\"/dashboard/store/payments/delivery-options/new\"");
        assertThat(html).contains("id=\"name\"").contains("Cena brutto (zł)").contains("id=\"description\"");
        assertThat(html).contains("<option value=\"Courier\" selected=\"selected\">Kurier</option>");
        assertThat(html).doesNotContain("Zmiana dotyczy też otwartych ofert");
    }

    @Test
    void theAccountPageReturnsToPayments() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", BankAccountForm.empty());
        variables.put("errors", Map.of());
        variables.put("currencies", CurrencyOptions.forPicker("PLN", Locale.forLanguageTag("pl")));
        variables.put("formAction", PATH + "/bank-accounts/new");
        variables.put("pageTitle", "Nowe konto bankowe");
        variables.put("alreadyDefault", false);
        variables.put("paymentsHref", PATH);
        variables.put("backLabel", "Płatności");

        // when
        String html = SettingsTemplateRenderer.render("store-bank-account", variables);

        // then
        assertThat(html).contains("action=\"/dashboard/store/payments/bank-accounts/new\"").contains("href=\"/dashboard/store/payments\"");
        assertThat(html).contains("id=\"iban\"").contains("Wymagany dla konta zagranicznego.").contains("name=\"makeDefault\"");
    }
}
