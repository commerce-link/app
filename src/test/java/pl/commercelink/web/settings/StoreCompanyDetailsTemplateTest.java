package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.CompanyDetailsForm;
import pl.commercelink.web.dtos.CountryOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCompanyDetailsTemplateTest {

    private Map<String, Object> page(CompanyDetailsForm form, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/company-details"));
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("countries", CountryOptions.forPicker(form.getCountry(), Locale.forLanguageTag("pl")));
        variables.put("formAction", "/dashboard/store/company-details");
        return variables;
    }

    private CompanyDetailsForm storedForm() {
        BillingDetails details = new BillingDetails();
        details.setCompanyName("Demo Store sp. z o.o.");
        details.setTaxId("1234567890");
        details.setCountry("PL");
        return CompanyDetailsForm.from(details);
    }

    @Test
    void groupsTheFieldsIntoCompanyAddressAndContactAndPostsBackToThePage() {
        // when
        String html = SettingsTemplateRenderer.render("store-company-details", page(storedForm(), Map.of()));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Szczegóły firmy</h1>");
        assertThat(html).contains("action=\"/dashboard/store/company-details\"");
        assertThat(html).contains(">Firma</legend>").contains(">Adres siedziby</legend>").contains(">Kontakt</legend>");
        assertThat(html).contains("value=\"Demo Store sp. z o.o.\"").contains("value=\"1234567890\"");
        assertThat(html).contains("<option value=\"PL\" selected=\"selected\">Polska</option>");
        assertThat(html).contains("class=\"cl-button is-primary\"").contains(">Zapisz zmiany<");
        assertThat(html).doesNotContain("cl-alert").doesNotContain("aria-invalid").doesNotContain("??");
    }

    @Test
    void keepsRelatedFieldsOnOneRowSoTheFormFitsALaptopScreen() {
        // when
        String html = SettingsTemplateRenderer.render("store-company-details", page(storedForm(), Map.of()));

        // then
        // Company name with tax ID, and postal code with city and country, share a row on wide screens; below 720px
        // every field takes the full width (commercelink.css).
        assertThat(html).containsPattern("class=\"cl-span-4\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"companyName\"");
        assertThat(html).containsPattern("class=\"cl-span-2\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"taxId\"");
        assertThat(html).containsPattern("class=\"cl-span-2\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"postalCode\"");
        assertThat(html).containsPattern("class=\"cl-span-2\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"city\"");
        assertThat(html).containsPattern("class=\"cl-span-2\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"country\"");
    }

    @Test
    void marksOnlyThePhoneAsOptionalAndGivesFieldsAutocompleteHints() {
        // when
        String html = SettingsTemplateRenderer.render("store-company-details", page(storedForm(), Map.of()));

        // then
        assertThat(html).containsOnlyOnce("class=\"cl-optional\"");
        assertThat(html).contains("id=\"phone\"").contains("type=\"tel\"");
        assertThat(html).contains("autocomplete=\"organization\"").contains("autocomplete=\"postal-code\"")
                .contains("autocomplete=\"email\"").contains("type=\"email\"");
    }

    @Test
    void listsEveryErrorAboveTheFormAndNextToItsField() {
        // given
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("taxId", "billing.taxId.required");
        errors.put("email", "billing.email.invalid");

        // when
        String html = SettingsTemplateRenderer.render("store-company-details", page(storedForm(), errors));

        // then
        assertThat(html).contains("id=\"form-errors\" class=\"cl-alert is-bad\"");
        assertThat(html).contains("Nie zapisano zmian. Popraw zaznaczone pola:");
        assertThat(html).contains("<a href=\"#taxId\">NIP jest wymagany</a>");
        assertThat(html).contains("<a href=\"#email\">Nieprawidłowy format email</a>");
        assertThat(html).contains("aria-invalid=\"true\" aria-describedby=\"taxId-error\"");
        assertThat(html).contains("id=\"taxId-error\"").contains("id=\"email-error\"");
        assertThat(html.split("cl-input is-invalid", -1)).hasSize(3);
        assertThat(html).doesNotContain("aria-describedby=\"city-error\"");
        assertThat(html).contains("getElementById('form-errors').focus()");
    }

    @Test
    void errorSummaryLinksScrollFieldsBelowTheStickyTopBar() throws Exception {
        // when
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);

        // then
        assertThat(css).containsPattern("\\.cl-input,\\s*\\.cl-select \\{\\s*scroll-margin-top: calc\\(var\\(--cl-topbar-height\\) \\+ 44px\\);");
    }

    @Test
    void keepsAStoredCountryThatIsNotOnTheListSelected() {
        // given
        CompanyDetailsForm form = storedForm();
        form.setCountry("Polska");

        // when
        String html = SettingsTemplateRenderer.render("store-company-details", page(form, Map.of()));

        // then
        assertThat(html).contains("<option value=\"Polska\" selected=\"selected\">Polska</option>");
        assertThat(html).contains("<option value=\"PL\">Polska</option>");
    }

    @Test
    void dropsBulmaFormMarkupAndTheOldEditEndpoint() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/store-company-details.html"), StandardCharsets.UTF_8);

        // then
        assertThat(template).doesNotContain("class=\"box\"").doesNotContain("class=\"input\"")
                .doesNotContain("\"button is-primary")
                .doesNotContain("company-details/edit").doesNotContain("store.storeId");
    }

    @Test
    void theFormFragmentRendersOnItsOwnForAsyncSavesAndCarriesTheSuccessMessage() {
        // given
        Map<String, Object> variables = page(storedForm(), Map.of());
        variables.put("savedMessage", "Dane firmy zostały pomyślnie zaktualizowane");

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-company-details :: companyDetailsForm}\"></div>", variables);

        // then
        assertThat(html.trim()).startsWith("<form");
        assertThat(html).contains("id=\"company-details-form\"").contains("data-cl-async");
        assertThat(html).contains("data-success-message=\"Dane firmy zostały pomyślnie zaktualizowane\"");
        assertThat(html).contains("data-error-message=\"Nie udało się zapisać zmian.");
        assertThat(html).doesNotContain("cl-page-title").doesNotContain("??");
    }

    @Test
    void theFullPageLoadsTheAsyncFormScriptAndLeavesTheSuccessMessageToTheFlash() {
        // when
        String html = SettingsTemplateRenderer.render("store-company-details", page(storedForm(), Map.of()));

        // then
        assertThat(html).contains("src=\"/js/async-form.js\"");
        assertThat(html).doesNotContain("data-success-message");
    }
}
