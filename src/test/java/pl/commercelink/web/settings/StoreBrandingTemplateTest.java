package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.BrandingForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreBrandingTemplateTest {

    private Map<String, Object> page(BrandingForm form, Map<String, String> errors, boolean hasLogo) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/branding"));
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("formAction", "/dashboard/store/branding");
        variables.put("hasLogo", hasLogo);
        variables.put("logoUrl", "/StoreLogo/store-1?v=42");
        variables.put("logoMaxBytes", BrandingForm.LOGO_MAX_BYTES);
        return variables;
    }

    private BrandingForm form(String color) {
        BrandingForm form = new BrandingForm();
        form.setStoreName("Demo Store");
        form.setPrimaryColor(color);
        return form;
    }

    private String render(BrandingForm form, Map<String, String> errors, boolean hasLogo) {
        return SettingsTemplateRenderer.render("store-branding", page(form, errors, hasLogo));
    }

    @Test
    void groupsTheFieldsIntoStoreLogoAndColourAndPostsTheFileBackToThePage() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Branding</h1>");
        assertThat(html).contains("action=\"/dashboard/store/branding\"").contains("enctype=\"multipart/form-data\"");
        assertThat(html).contains(">Sklep</legend>").contains(">Logo</legend>").contains(">Kolor</legend>");
        assertThat(html).contains("id=\"storeName\"").contains("value=\"Demo Store\"");
        assertThat(html).contains("class=\"cl-button is-primary\"").contains(">Zapisz zmiany<");
        assertThat(html).doesNotContain("cl-alert").doesNotContain("aria-invalid").doesNotContain("??");
    }

    @Test
    void marksTheLogoAndTheColourAsOptional() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html.split("class=\"cl-optional\"", -1)).hasSize(3);
        assertThat(html).containsPattern("for=\"logoFile\">\\s*<span>Plik logo</span>\\s*<span class=\"cl-optional\"");
        assertThat(html).containsPattern("for=\"primaryColor\">\\s*<span>Kolor przewodni</span>\\s*<span class=\"cl-optional\"");
    }

    @Test
    void showsTheCurrentLogoAsAnImageWithAnOptionToRemoveIt() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html).contains("src=\"/StoreLogo/store-1?v=42\"").contains("alt=\"Obecne logo sklepu\"");
        assertThat(html).contains("name=\"removeLogo\"").contains("Usuń logo przy zapisie");
        assertThat(html).contains("accept=\"image/png,image/jpeg,image/webp,image/gif\"");
        assertThat(html).contains("aria-describedby=\"logoFile-help\"").contains("PNG, JPG, WebP lub GIF, maks. 1 MB");
        assertThat(html).contains("data-max-bytes=\"1048576\"");
        assertThat(html).doesNotContain("/StoreLogo/store-1\"").doesNotContain("readonly");
    }

    @Test
    void withoutALogoSaysSoAndOffersNothingToRemove() {
        // when
        String html = render(form("#1b4db1"), Map.of(), false);

        // then
        assertThat(html).contains("Brak logo");
        assertThat(html).doesNotContain("src=\"/StoreLogo").doesNotContain("name=\"removeLogo\"");
    }

    @Test
    void previewsTheColourOnASampleButton() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html).contains("style=\"--cl-brand: #1b4db1\"").contains("class=\"cl-brand-sample\"");
        assertThat(html).contains("value=\"#1b4db1\"").contains("data-cl-color-field");
    }
    @Test
    void labelsTheSampleAsACustomerPagePreviewSoItIsNotMistakenForAnAction() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html).containsPattern("class=\"cl-brand-preview\" aria-hidden=\"true\"[^>]*>\\s*<span class=\"cl-brand-preview-label\">Przykładowy wygląd</span>");
    }

    @Test
    void keepsTheLogoOptionsAndTheColourPreviewOnTheRowOfTheirFieldSoTheFormFitsALaptopScreen() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html).containsPattern("<div class=\"cl-file-row\">\\s*<input class=\"cl-file\"[^>]*>\\s*<label class=\"cl-check\">");
        assertThat(html).containsPattern("(?s)<div class=\"cl-color-row\">(?:(?!</div>).)*class=\"cl-brand-preview\"");
    }

    @Test
    void showsNoContrastNoticeWhateverTheColour() {
        // when
        String unreadable = render(form("#f5a623"), Map.of(), true);
        String empty = render(form(""), Map.of(), true);
        String invalid = render(form("blue"), Map.of("primaryColor", "store.branding.color.invalid"), true);

        // then
        for (String html : new String[]{unreadable, empty, invalid}) {
            assertThat(html).doesNotContain("Kontrast").doesNotContain("data-cl-contrast").doesNotContain("cl-status")
                    .doesNotContain("Bez koloru strony klienta").doesNotContain("aby zobaczyć podgląd");
        }
    }
    @Test
    void hidesTheSampleWithoutAColour() {
        // when
        String html = render(form(""), Map.of(), true);

        // then
        assertThat(html).containsPattern("class=\"cl-brand-preview\" aria-hidden=\"true\" data-cl-brand-sample hidden=\"hidden\"");
        assertThat(html).doesNotContain("--cl-brand:");
    }
    @Test
    void neverWritesAnInvalidColourIntoAStyleAttribute() {
        // given
        Map<String, String> errors = Map.of("primaryColor", "store.branding.color.invalid");

        // when
        String html = render(form("red;background:url(https://example.com/x)"), errors, true);

        // then
        assertThat(html).doesNotContain("--cl-brand:").doesNotContain("style=\"red");
        assertThat(html).contains("value=\"red;background:url(https://example.com/x)\"");
    }

    @Test
    void listsEveryErrorAboveTheFormAndNextToItsField() {
        // given
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("storeName", "store.branding.name.required");
        errors.put("logoFile", "store.branding.logo.invalid.type");
        errors.put("primaryColor", "store.branding.color.invalid");

        // when
        String html = render(form("nope"), errors, true);

        // then
        assertThat(html).contains("id=\"form-errors\" class=\"cl-alert is-bad\"");
        assertThat(html).contains("Nie zapisano zmian. Popraw zaznaczone pola:");
        assertThat(html).contains("<a href=\"#storeName\">Podaj nazwę sklepu</a>");
        assertThat(html).contains("<a href=\"#logoFile\">Logo musi być obrazem PNG, JPG, WebP lub GIF</a>");
        assertThat(html).contains("<a href=\"#primaryColor\">Podaj kolor w formacie #RRGGBB, np. #1b4db1</a>");
        assertThat(html).contains("aria-invalid=\"true\" aria-describedby=\"storeName-error\"");
        assertThat(html).contains("aria-invalid=\"true\" aria-describedby=\"logoFile-help logoFile-error\"");
        assertThat(html).contains("aria-invalid=\"true\" aria-describedby=\"primaryColor-help primaryColor-error\"");
        assertThat(html).contains("getElementById('form-errors').focus()");
    }

    @Test
    void theFormFragmentRendersOnItsOwnForAsyncSavesAndCarriesTheSuccessMessage() {
        // given
        Map<String, Object> variables = page(form("#1b4db1"), Map.of(), true);
        variables.put("savedMessage", "Zapisano");

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-branding :: brandingForm}\"></div>", variables);

        // then
        assertThat(html.trim()).startsWith("<form");
        assertThat(html).contains("id=\"branding-form\"").contains("data-cl-async");
        assertThat(html).contains("data-success-message=\"Zapisano\"");
        assertThat(html).contains("data-error-message=\"Nie udało się zapisać zmian.");
        assertThat(html).doesNotContain("cl-page-title").doesNotContain("??");
    }

    @Test
    void theFullPageLoadsTheFormScriptsAndLeavesTheSuccessMessageToTheFlash() {
        // when
        String html = render(form("#1b4db1"), Map.of(), true);

        // then
        assertThat(html).contains("src=\"/js/async-form.js\"").contains("src=\"/js/color-field.js\"").contains("src=\"/js/image-field.js\"");
        assertThat(html).doesNotContain("data-success-message");
    }

    @Test
    void dropsBulmaMarkupTheConfirmModalTheSecondaryColourAndTheOldEditEndpoint() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/store-branding.html"), StandardCharsets.UTF_8);

        // then
        assertThat(template).doesNotContain("class=\"box\"").doesNotContain("class=\"input\"")
                .doesNotContain("\"button is-primary").doesNotContain("file has-name")
                .doesNotContain("confirmSave").doesNotContain("secondary")
                .doesNotContain("branding/edit").doesNotContain("storeId");
    }
}
