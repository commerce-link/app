package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationPagesRenderingTest {

    private static final List<String> TEMPLATES = List.of("register", "register-password", "register-verify-email",
            "register-success", "logout-success", "signup-layout", "auth-layout", "fragments/signup");

    private static Map<String, Object> registerModel(boolean demoMode) {
        Map<String, Object> model = new HashMap<>();
        model.put("demoMode", demoMode);
        model.put("stepTotal", demoMode ? 2 : 3);
        model.put("ttlDays", 7);
        model.put("trialDays", 14);
        model.put("trialRetentionDays", 14);
        model.put("storeNameMaxLength", 60);
        model.put("termsUrl", "https://example.com/regulamin");
        model.put("termsConsent", false);
        model.put("fieldErrors", new HashMap<String, String>());
        model.put("autofocus", "email");
        return model;
    }

    private static String render(String template, Map<String, Object> model) {
        return SettingsTemplateRenderer.render(template, model);
    }

    private static String source(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + template + ".html"));
    }

    @Test
    void trialRegistrationShowsTheFormNextToTheAppPanel() {
        // when
        String html = render("register", registerModel(false));

        // then
        assertThat(html).contains("<html lang=\"pl\"").contains("name=\"viewport\"")
                .contains("<title>Rejestracja · CommerceLink</title>")
                .contains("14 dni za darmo").contains("Ty sprzedajesz.").contains("My robimy resztę.")
                .contains("name=\"storeName\"").contains("Zmienisz ją później")
                .contains("href=\"https://example.com/regulamin\"").contains("Regulamin serwisu")
                .contains("Zacznij 14 dni za darmo").contains("Bez karty płatniczej")
                .contains("Po okresie próbnym panel przez 14 dni działa tylko do podglądu")
                .contains("Masz już konto?").contains("href=\"/login\"")
                .contains("class=\"cl-signup-panel\"").contains("Przykładowe dane").contains("W realizacji")
                .doesNotContain("Krok 1").doesNotContain("cf-turnstile").doesNotContain("??");
        assertThat(html).containsPattern(Pattern.compile("id=\"email\"[^>]*autofocus"));
        assertThat(html).containsPattern(Pattern.compile("id=\"storeName\"[^>]*maxlength=\"60\""));
    }

    @Test
    void demoRegistrationAsksOnlyForTheEmail() {
        // when
        String html = render("register", registerModel(true));

        // then
        assertThat(html).contains("Wersja demonstracyjna").contains("Załóż konto")
                .contains("zostanie automatycznie usunięte po 7 dniach")
                .doesNotContain("name=\"storeName\"").doesNotContain("Bez karty płatniczej").doesNotContain("??");
    }

    @Test
    void fieldErrorStandsUnderItsFieldAndTakesTheFocus() {
        // given
        Map<String, Object> model = registerModel(false);
        model.put("fieldErrors", Map.of("storeName", "registration.error.invalid-store-name"));
        model.put("autofocus", "storeName");
        model.put("storeName", "S");

        // when
        String html = render("register", model);

        // then
        assertThat(html).contains("id=\"storeName-error\"").contains("Nazwa sklepu musi mieć od 2 do 60 znaków.")
                .containsPattern(Pattern.compile("id=\"storeName\"[^>]*autofocus"))
                .containsPattern(Pattern.compile("id=\"storeName\"[^>]*aria-invalid=\"true\""))
                .containsPattern(Pattern.compile("aria-describedby=\"storeName-help storeName-error\""))
                .doesNotContainPattern(Pattern.compile("id=\"email\"[^>]*autofocus"))
                .doesNotContain("cl-alert is-bad");
    }

    @Test
    void termsErrorStandsUnderTheCheckboxThatKeepsItsState() {
        // given
        Map<String, Object> model = registerModel(false);
        model.put("fieldErrors", Map.of("termsConsent", "registration.error.terms-consent-required"));
        model.put("autofocus", "termsConsent");

        // when
        String html = render("register", model);

        // then
        assertThat(html).contains("id=\"termsConsent-error\"").contains("zaakceptuj regulamin serwisu")
                .containsPattern(Pattern.compile("id=\"termsConsent\"[^>]*aria-describedby=\"termsConsent-error\""));

        // when
        model.put("fieldErrors", Map.of("email", "registration.error.email-exists"));
        model.put("termsConsent", true);
        String afterEmailError = render("register", model);

        // then
        assertThat(afterEmailError).containsPattern(Pattern.compile("id=\"termsConsent\"[^>]*checked"));
    }

    @Test
    void errorWithoutAFieldIsAnAlertAboveTheForm() {
        // given
        Map<String, Object> model = registerModel(false);
        model.put("errorMessage", "Potwierdź, że nie jesteś robotem, i spróbuj ponownie.");
        model.put("captchaSiteKey", "site-key");

        // when
        String html = render("register", model);

        // then
        assertThat(html).contains("cl-alert is-bad").contains("Potwierdź, że nie jesteś robotem")
                .contains("data-sitekey=\"site-key\"").contains("data-appearance=\"interaction-only\"")
                .contains("challenges.cloudflare.com/turnstile");
        assertThat(html.indexOf("/js/turnstile-size.js")).isPositive()
                .isGreaterThan(html.indexOf("class=\"cf-turnstile\""))
                .isLessThan(html.indexOf("challenges.cloudflare.com/turnstile/v0/api.js"));
    }

    @Test
    void passwordStepShowsTheStepAndTheAddressAndNeverTheTypedPassword() {
        // given
        Map<String, Object> model = registerModel(false);
        model.put("email", "anna@sklep.pl");
        model.put("autofocus", "password");

        // when
        String html = render("register-password", model);

        // then
        assertThat(html).contains("Krok 2 z 3").contains("Ustaw hasło").contains("<strong>anna@sklep.pl</strong>")
                .contains("rozpocząć 14\u2011dniowy okres próbny").contains("Utwórz konto")
                .contains("Minimum 8 znaków").contains("Wróć i zmień adres e-mail").contains("Masz już konto?")
                .doesNotContainPattern(Pattern.compile("id=\"password\"[^>]*value=\"[^\"]+\""))
                .doesNotContain("??");
    }

    @Test
    void demoPasswordStepCountsTwoSteps() {
        // given
        Map<String, Object> model = registerModel(true);
        model.put("email", "anna@sklep.pl");

        // when
        String html = render("register-password", model);

        // then
        assertThat(html).contains("Krok 2 z 2").contains("od razu wejdziesz do panelu");
    }

    @Test
    void codeStepHasNoSignInLinkBecauseTheUserIsAlreadySignedIn() {
        // given
        Map<String, Object> model = new HashMap<>();
        model.put("email", "anna@sklep.pl");
        model.put("fieldErrors", Map.of("code", "registration.error.invalid-code"));
        model.put("autofocus", "code");
        model.put("infoMessage", "Wysłaliśmy nowy kod. Sprawdź skrzynkę e-mail.");

        // when
        String html = render("register-verify-email", model);

        // then
        assertThat(html).contains("Krok 3 z 3").contains("Potwierdź adres e-mail").contains("<strong>anna@sklep.pl</strong>")
                .contains("Kod jest nieprawidłowy lub wygasł.").contains("cl-alert is-ok")
                .contains("action=\"/register/verify-email/resend\"").contains("Wyślij kod ponownie")
                .containsPattern(Pattern.compile("id=\"code\"[^>]*inputmode=\"numeric\""))
                .doesNotContain("Masz już konto?").doesNotContain("??");
    }

    @Test
    void endPagesAreOneCardOnTheAppBackground() {
        // given
        Map<String, Object> model = new HashMap<>();
        model.put("email", "anna@sklep.pl");
        model.put("demoMode", false);

        // when
        String success = render("register-success", model);
        String logout = render("logout-success", Map.of());

        // then
        assertThat(success).contains("<body class=\"cl-auth\">").contains("Konto utworzone!")
                .contains("aplikację uwierzytelniającą").contains("href=\"/login\"").doesNotContain("??");
        assertThat(logout).contains("<body class=\"cl-auth\">").contains("<html lang=\"pl\"")
                .contains("href=\"/oauth2/authorization/cognito\"").doesNotContain("??");
    }

    @Test
    void pagesUseTheDesignSystemWithoutLocalStyles() throws Exception {
        // when / then
        for (String template : TEMPLATES) {
            String html = source(template);
            assertThat(html).as(template).doesNotContain("<style").doesNotContain("style=")
                    .doesNotContainPattern(Pattern.compile("(?<!cl-)\\bbutton is-primary"))
                    .doesNotContain("th:utext").doesNotContain("placeholder=");
        }
    }

    @Test
    void signUpStylesUseTokensOnly() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));
        String section = css.substring(css.indexOf("Sign-up and auth pages"));

        // when / then
        assertThat(section).doesNotContainPattern(Pattern.compile("#[0-9a-fA-F]{3,6}\\b")).doesNotContain("gradient");
    }
}
