package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.render;

class AuthErrorPagesRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Test
    void notFoundIsAnAuthCardWithTheWayToTheDashboard() {
        // when
        String html = render("error/404", PL, Map.of());

        // then
        assertThat(html).contains("<html lang=\"pl\">")
                .contains("<title>Nie ma takiej strony · CommerceLink</title>")
                .contains("<body class=\"cl-auth\">")
                .contains("<h1 class=\"cl-page-title\">Nie ma takiej strony</h1>")
                .contains("Adres jest nieprawidłowy albo strona została przeniesiona.")
                .contains("<a class=\"cl-button is-primary\" href=\"/dashboard\">Przejdź do panelu</a>")
                .doesNotContain("The page you are looking for");
        assertThat(pageSource("error/404")).doesNotContain("cdn.jsdelivr").doesNotContain("fontawesome").doesNotContain("fa-");
    }

    @Test
    void notFoundInEnglish() {
        // when
        String html = render("error/404", Locale.ENGLISH, Map.of());

        // then
        assertThat(html).contains("<title>Page not found · CommerceLink</title>")
                .contains("This page does not exist")
                .contains(">Go to the dashboard</a>");
    }

    @Test
    void accessDeniedNamesTheAccountAndOffersTheDashboardAndSigningOut() {
        // given
        Map<String, Object> model = new HashMap<>();
        model.put("userEmail", "store-user@example.com");

        // when
        String html = render("error/403", PL, model);

        // then
        assertThat(html).contains("<title>Brak dostępu · CommerceLink</title>")
                .contains("<h1 class=\"cl-page-title\">Nie masz dostępu do tej strony</h1>")
                .contains("poproś administratora sklepu o dostęp")
                .contains("<p class=\"cl-auth-meta\">Zalogowano jako store-user@example.com</p>")
                .contains("<div class=\"cl-card-footer is-stacked\">")
                .contains("<a class=\"cl-button is-primary\" href=\"/dashboard\">Przejdź do panelu</a>")
                .contains("<a class=\"cl-button\" href=\"/logout\">Wyloguj się</a>")
                .doesNotContain("Access Denied");
    }

    @Test
    void accessDeniedWithoutAKnownAccountSkipsTheAccountLine() {
        // when
        String html = render("error/403", Locale.ENGLISH, Map.of());

        // then
        assertThat(html).contains("<title>Access denied · CommerceLink</title>")
                .contains("You don&#39;t have access to this page")
                .contains(">Sign out</a>")
                .doesNotContain("cl-auth-meta");
    }

    @Test
    void bothPagesHaveOneHeading() {
        // when / then
        assertThat(render("error/404", PL, Map.of()).split("<h1", -1)).hasSize(2);
        assertThat(render("error/403", PL, Map.of()).split("<h1", -1)).hasSize(2);
    }

    private static String pageSource(String view) {
        try {
            return new String(AuthErrorPagesRenderingTest.class.getResourceAsStream("/templates/" + view + ".html").readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
