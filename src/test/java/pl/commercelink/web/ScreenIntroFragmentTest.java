package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenIntroFragmentTest {

    private static String panel(String screen) {
        return SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/screen-intro :: panel('" + screen + "', 'fas fa-cubes')}\"></div>", Map.of());
    }

    private static String toggle() {
        return SettingsTemplateRenderer.render("<button th:replace=\"~{fragments/screen-intro :: toggle}\"></button>", Map.of());
    }

    private static String guideStyles() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));
        int start = css.indexOf("/* Screen guide (fragments/screen-intro)");
        return css.substring(start, css.indexOf(".cl-page .cl-page-title {", start));
    }

    private static int items(String html) {
        return html.split("class=\"screen-intro-check\"", -1).length - 1;
    }

    @Test
    void listsAsManyItemsAsTheScreenDefines() {
        // when
        String assortment = panel("inventory");
        String prices = panel("inventory-prices");
        String technical = panel("inventory-tech");

        // then
        assertThat(items(assortment)).isEqualTo(5);
        assertThat(assortment).contains("Przeglądaj po kategoriach").contains("Wychwyć braki");
        assertThat(assortment.indexOf("Jeden wspólny układ")).isNotNegative().isLessThan(assortment.indexOf("Przeglądaj po kategoriach"));
        assertThat(items(prices)).isEqualTo(2);
        assertThat(prices).contains("Sprawdź cenę produktu").doesNotContain("Wychwyć braki");
        assertThat(items(technical)).isEqualTo(3);
        assertThat(assortment + prices + technical).doesNotContain("??");
    }

    @Test
    void screenWithThreeItemsStillListsExactlyThree() {
        // when
        String orders = panel("orders");

        // then
        assertThat(items(orders)).isEqualTo(3);
        assertThat(orders.split("<li>", -1)).hasSize(4);
        assertThat(orders).doesNotContain("??").doesNotContainPattern("<li>\\s*</li>");
    }

    @Test
    void panelUsesTheSharedLayerWithoutInlineStylesOrHandlers() {
        // when
        String orders = panel("orders");

        // then
        assertThat(orders).doesNotContain("<style").doesNotContain("style=").doesNotContain("onclick=")
                .doesNotContain("button is-link")
                .contains("class=\"cl-button is-primary\" data-intro-dismiss")
                .contains("class=\"cl-icon-button screen-intro-close\" data-intro-dismiss")
                .contains("<h2 class=\"screen-intro-title\">")
                .contains("id=\"screen-intro-orders\"")
                .contains("/js/screen-intro.js\"></script>");
    }

    @Test
    void toggleIsNamedForScreenReadersAndBoundByScript() {
        // when
        String html = toggle();

        // then
        assertThat(html).doesNotContain("onclick=").contains("data-intro-toggle").contains("aria-label=\"Pokaż opis ekranu\"");
    }

    @Test
    void guideStylesTakeColoursAndShapeFromTheTokens() throws Exception {
        // when
        String css = guideStyles();

        // then
        assertThat(css).doesNotContainPattern("#[0-9a-fA-F]{3,6}\\b").doesNotContain("gradient")
                .contains("background: var(--cl-accent);")
                .contains("border-radius: var(--cl-radius);")
                .contains("box-shadow: var(--cl-shadow-card);")
                .contains(".screen-intro-toggle:focus-visible {");
    }

    @Test
    void scriptBindsTheButtonsInsteadOfGlobalFunctions() throws Exception {
        // when
        String script = Files.readString(Path.of("src/main/resources/static/js/screen-intro.js"));

        // then
        assertThat(script).contains("addEventListener('click'").doesNotContain("window.toggleScreenIntro")
                .doesNotContain("window.dismissScreenIntro");
    }
}
