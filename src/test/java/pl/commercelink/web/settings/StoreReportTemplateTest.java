package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.StoreReportingSettingsController.ConversionsAddress;
import pl.commercelink.web.dtos.ReportingForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StoreReportTemplateTest {

    private static final String URL = "https://api.example.com/Store/store-1/Reporting/Google/Conversions/74f99509-aaaa-bbbb-cccc-444455556666";
    private static final String MASKED = "https://api.example.com/Store/store-1/Reporting/Google/Conversions/••••••••56666";

    private Map<String, Object> page(boolean enabled, ConversionsAddress address, boolean setupOpen) {
        ReportingForm form = new ReportingForm();
        form.setGoogleAdsEnabled(enabled);
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/report"));
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", Map.of());
        variables.put("formAction", "/dashboard/store/report");
        variables.put("conversionsAddress", address);
        variables.put("setupOpen", setupOpen);
        variables.put("newAddressHref", "/dashboard/store/report/google-ads/new-address");
        variables.put("conversionName", "Zakupy offline");
        variables.put("reportsHref", "/dashboard/reports");
        return variables;
    }

    private String switchedOn() {
        return SettingsTemplateRenderer.render("store-report", page(true, new ConversionsAddress(URL, MASKED), false));
    }

    @Test
    void theSwitchInTheCardHeaderSendsTheOppositeStateAndThereIsNoSaveButton() {
        // when
        String html = switchedOn();

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Raportowanie</h1>");
        assertThat(html).containsPattern("<div class=\"cl-card-head\">\\s*<h2 class=\"cl-card-title\" id=\"google-ads-title\">Google Ads — konwersje offline</h2>\\s*<button[^>]*role=\"switch\"");
        assertThat(html).containsPattern("<button type=\"submit\" class=\"cl-switch\" id=\"google-ads-switch\" role=\"switch\"\\s+aria-labelledby=\"google-ads-title\" aria-checked=\"true\">");
        assertThat(html).contains("<input type=\"hidden\" name=\"googleAdsEnabled\" value=\"false\"");
        assertThat(html).containsPattern("cl-swap-a\">Wyłączone</span>\\s*<span class=\"cl-swap-b\">Włączone</span>");
        assertThat(html).doesNotContain("Zapisz zmiany").doesNotContain("cl-card-footer").doesNotContain("data-cl-reveal=");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void switchedOnTheAddressSpansTheCardWithCopyShowAndNewAddressInOneRowBelow() {
        // when
        String html = switchedOn();

        // then
        assertThat(html).contains("id=\"reporting-form\" class=\"cl-form cl-card is-wide\"");
        assertThat(html).contains("data-value=\"" + URL + "\"").contains("data-masked=\"" + MASKED + "\"");
        assertThat(html).containsPattern("class=\"cl-copy-value\" data-cl-copy-value hidden[\\s\\S]*?<div class=\"cl-copy-actions\">"
                + "[\\s\\S]*?data-cl-copy-button[\\s\\S]*?data-cl-reveal-button[\\s\\S]*?<a class=\"cl-button cl-copy-actions-end\"[^>]*href=\"/dashboard/store/report/google-ads/new-address\"");
        assertThat(html).containsPattern("class=\"cl-copy-status cl-visually-hidden\"[^>]*role=\"status\"");
        assertThat(html).contains("<summary>Pokaż adres</summary>").contains("Działa jak hasło");
        assertThat(html).contains("id=\"google-ads-new-address\"").contains("data-cl-confirm data-cl-confirm-async");
        assertThat(html).contains("data-cl-confirm-action=\"Wygeneruj nowy adres\"").contains("<dialog class=\"cl-dialog\" id=\"cl-confirm-dialog\"");
    }

    @Test
    void copyAndShowKeepTheirSizeBecauseBothStatesAreRenderedInOneSlot() {
        // when
        String html = switchedOn();

        // then
        assertThat(html).containsPattern("data-cl-copy-button[\\s\\S]*?class=\"cl-swap\"[\\s\\S]*?fa-copy[\\s\\S]*?fa-check[\\s\\S]*?Kopiuj");
        assertThat(html).containsPattern("data-cl-reveal-button[\\s\\S]*?class=\"cl-swap\"[\\s\\S]*?fa-eye[\\s\\S]*?Pokaż[\\s\\S]*?fa-eye-slash[\\s\\S]*?Ukryj");
        assertThat(html).doesNotContain("aria-pressed").contains("data-copied-text=\"Skopiowano adres\"");
    }

    @Test
    void theInstructionsAreCollapsedOnAnOrdinaryVisit() {
        // when
        String html = switchedOn();

        // then
        assertThat(html).containsPattern("<details class=\"cl-disclosure\" id=\"google-ads-setup\">\\s*<summary>Jak podłączyć w Google Ads\\?</summary>");
        assertThat(html).contains("<ol class=\"cl-steps\"").contains("<code class=\"cl-code\">Zakupy offline</code>")
                .contains("automatyczne tagowanie").contains("HTTPS");
        assertThat(html.indexOf("data-cl-copy-field")).isLessThan(html.indexOf("cl-disclosure"));
    }

    @Test
    void theFirstSwitchOnOpensTheInstructions() {
        // when
        String html = SettingsTemplateRenderer.render("store-report", page(true, new ConversionsAddress(URL, MASKED), true));

        // then
        assertThat(html).contains("<details class=\"cl-disclosure\" id=\"google-ads-setup\" open=\"open\">");
    }

    @Test
    void switchedOffTheCardHoldsOnlyTheSwitchAndTheDescription() {
        // when
        String html = SettingsTemplateRenderer.render("store-report", page(false, null, false));

        // then
        assertThat(html).contains("aria-checked=\"false\"").contains("<input type=\"hidden\" name=\"googleAdsEnabled\" value=\"true\"");
        assertThat(html).contains("Google Ads raz dziennie pobiera wczorajsze zamówienia z reklam.");
        assertThat(html).doesNotContain("data-cl-copy-field").doesNotContain("cl-disclosure").doesNotContain("/google-ads/new-address")
                .doesNotContain("Zakupy offline");
    }

    @Test
    void theTokenIsNeverAFormFieldSoSavingCannotReplaceIt() {
        // when
        String html = switchedOn();

        // then
        assertThat(html).doesNotContain("name=\"googleAdsToken\"").doesNotContain("store.storeId").doesNotContain("readonly");
        assertThat(Pattern.compile("<(input|select|textarea)[^>]*\\sname=\"").matcher(html).results().count()).isEqualTo(1);
    }

    @Test
    void earlierDaysAreDownloadedFromReports() {
        // when
        String html = switchedOn();

        // then
        assertThat(html).contains("href=\"/dashboard/reports\"").contains("Przejdź do raportów");
    }

    @Test
    void withoutALinkToReportsTheHintIsLeftOut() {
        // given
        Map<String, Object> variables = page(true, new ConversionsAddress(URL, MASKED), false);
        variables.remove("reportsHref");

        // when
        String html = SettingsTemplateRenderer.render("store-report", variables);

        // then
        assertThat(html).doesNotContain("/dashboard/reports").doesNotContain("Przejdź do raportów");
    }

    @Test
    void theFormRendersOnItsOwnForAsyncSaves() {
        // given
        Map<String, Object> variables = page(true, new ConversionsAddress(URL, MASKED), false);
        variables.put("savedMessage", "Zapisano");

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-report :: reportingForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("id=\"reporting-form\"").contains("data-success-message=\"Zapisano\"")
                .contains("role=\"switch\"");
    }

    @Test
    void dropsBulmaMarkup() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/store-report.html"), StandardCharsets.UTF_8);

        // then
        assertThat(template).doesNotContain("class=\"box\"").doesNotContain("\"button is-primary").doesNotContain("style=")
                .doesNotContain("general.cancel");
    }
}
