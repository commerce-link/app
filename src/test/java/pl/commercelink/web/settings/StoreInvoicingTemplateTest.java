package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.dtos.InvoicingSettingsForm;
import pl.commercelink.stores.InvoicingConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreInvoicingTemplateTest {

    private static final String PATH = "/dashboard/store/invoicing";
    private static final String SYSTEM = "fakturownia";

    private static InvoicingProviderDescriptor provider() {
        InvoicingProviderDescriptor descriptor = mock(InvoicingProviderDescriptor.class);
        when(descriptor.name()).thenReturn(SYSTEM);
        when(descriptor.displayName()).thenReturn("Fakturownia");
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("domain", "Domena", FieldType.TEXT, true, "firma.fakturownia.pl"),
                new ProviderField("apiToken", "Token API", FieldType.PASSWORD, true, null)));
        return descriptor;
    }

    private Map<String, Object> page(IntegrationStatus status) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("systemStatus", status);
        variables.put("systemHref", PATH + "/system");
        variables.put("disconnectHref", PATH + "/system/disconnect");
        variables.put("invoicesForm", InvoicingSettingsForm.from(new InvoicingConfiguration()));
        variables.put("invoicesErrors", Map.of());
        variables.put("invoicesAction", PATH);
        return variables;
    }

    private String rendered(IntegrationStatus status) {
        return SettingsTemplateRenderer.render("store-invoicing", page(status));
    }

    private String configured() {
        return rendered(new IntegrationStatus(SYSTEM, "Fakturownia", true, true));
    }

    private Map<String, Object> systemPage(String providerName, Map<String, String> stored, Set<String> storedSecretIds,
                                           Map<String, String> errors) {
        InvoicingProviderDescriptor provider = provider();
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", IntegrationSettingsForm.from(providerName, stored, provider.configurationFields()));
        variables.put("errors", errors);
        variables.put("errorLabels", Map.of());
        variables.put("providers", List.of(provider));
        variables.put("providerKnown", SYSTEM.equals(providerName));
        variables.put("storedSecretIds", storedSecretIds);
        variables.put("formAction", PATH + "/system");
        variables.put("invoicingHref", PATH);
        variables.put("backLabel", "Fakturowanie");
        variables.put("pageTitle", "Zmień system fakturowy");
        return variables;
    }

    /** One form on the page: the system is a status with a link to its own page, so "Save changes" appears once. */
    @Test
    void thePageCarriesOneFormBelowTheSystemStatusAndNoBankAccounts() {
        // when
        String html = configured();

        // then
        assertThat(html.indexOf("id=\"invoicing-system-title\"")).isLessThan(html.indexOf("id=\"invoicing-invoices-form\""));
        assertThat(html).doesNotContain("bank-accounts-title");
        assertThat(html).containsOnlyOnce("data-cl-async").doesNotContain("id=\"invoicing-system-form\"");
        assertThat(html.split(">Zapisz zmiany<", -1)).hasSize(2);
        assertThat(html).contains("cl-card is-status").contains("action=\"/dashboard/store/invoicing\"");
        assertThat(html).doesNotContain("name=\"store.storeId\"").doesNotContain("name=\"storeId\"");
    }

    /** "Connected" was shown for any provider name; nothing tests the connection. */
    @Test
    void aConfiguredSystemNamesItselfAndOffersChangeAndDisconnect() {
        // when
        String html = configured();

        // then
        assertThat(html).contains("Faktury wystawia Fakturownia").contains("fa-check-circle is-ok").doesNotContain("Połączony");
        assertThat(html).contains("href=\"/dashboard/store/invoicing/system\"").contains(">Zmień<");
        assertThat(html).contains("data-cl-confirm-title=\"Odłączyć Fakturownia?\"");
    }

    @Test
    void withoutASystemThePageSaysNoInvoicesAreIssuedAndOffersTheChoice() {
        // when
        String html = rendered(IntegrationStatus.none());

        // then
        assertThat(html).contains("Faktury nie są wystawiane").contains(">Wybierz system<").contains("fa-exclamation-triangle is-warn");
        assertThat(html).doesNotContain("Odłącz");
    }

    @Test
    void anIncompleteOrMissingSystemSaysWhatIsWrong() {
        // when
        String incomplete = rendered(new IntegrationStatus(SYSTEM, "Fakturownia", true, false));
        String missing = rendered(new IntegrationStatus("retired", "retired", false, false));

        // then
        assertThat(incomplete).contains("Brakuje danych dostępu do Fakturownia").contains(">Uzupełnij<");
        assertThat(missing).contains("System retired nie jest dostępny").contains(">Zmień<");
    }

    @Test
    void theSystemPageShowsTheStoredSettingsButNeverTheSecret() {
        // when
        String html = SettingsTemplateRenderer.render("store-invoicing-system", systemPage(SYSTEM,
                Map.of("domain", "firma.fakturownia.pl", "apiToken", ""), Set.of("setting-" + SYSTEM + "-apiToken"), Map.of()));

        // then
        assertThat(html.replaceAll("\\s+", " ")).contains("<option value=\"fakturownia\" selected=\"selected\">Fakturownia</option>");
        assertThat(html).contains("value=\"firma.fakturownia.pl\"").contains("Zapisany. Zostaw puste, żeby go nie zmieniać.");
        assertThat(html).contains("data-cl-variant-select=\"invoicing-provider\"").contains("data-cl-variant=\"fakturownia\"");
        assertThat(html).contains(">Zapisz system<").contains("href=\"/dashboard/store/invoicing\"");
        assertThat(html).contains("/js/variant-fields.js").contains("/js/async-form.js");
    }

    @Test
    void theSystemPageAsksForAChoiceWhenNoneIsKnown() {
        // when
        String html = SettingsTemplateRenderer.render("store-invoicing-system", systemPage(null, Map.of(), Set.of(), Map.of()));

        // then
        assertThat(html).contains("<option value=\"\">Wybierz system</option>");
    }

    @Test
    void theInvoiceSettingsExplainThemselvesInPolishAndRevealThePrefixWithConsolidation() {
        // when
        String html = configured();

        // then
        assertThat(html).contains("Termin płatności (dni)").contains("0 to płatność od razu").doesNotContain("Number of days");
        assertThat(html).contains("data-cl-reveal=\"invoicing-consolidation-prefix\"").contains("id=\"invoicing-consolidation-prefix\"");
        assertThat(html).contains("Proforma jest dołączana zawsze");
    }

    @Test
    void loadsTheScriptsAndDropsTheBulmaTableAndConfirmPopup() {
        // when
        String html = configured();

        // then
        assertThat(html).contains("/js/async-form.js").contains("/js/reveal.js").contains("/js/confirm-dialog.js");
        assertThat(html).doesNotContain("onclick=\"confirmSave(this)\"").doesNotContain("table is-fullwidth")
                .doesNotContain(">Cancel<");
    }

    @Test
    void theInvoicesFormRendersAloneForASaveWithoutReloading() {
        // when
        String invoices = SettingsTemplateRenderer.render("<div th:replace=\"~{store-invoicing :: invoicesForm}\"></div>",
                page(IntegrationStatus.none()));

        // then
        assertThat(invoices).contains("id=\"invoicing-invoices-form\"").doesNotContain("invoicing-system-title");
    }
}
