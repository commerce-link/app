package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenIntroFragmentTest {

    private static String panel(String screen) {
        return SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/screen-intro :: panel('" + screen + "', 'fas fa-cubes')}\"></div>", Map.of());
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
        assertThat(items(assortment)).isEqualTo(4);
        assertThat(assortment).contains("Przeglądaj po kategoriach").contains("Wychwyć braki");
        assertThat(items(prices)).isEqualTo(2);
        assertThat(prices).contains("Sprawdź cenę produktu").doesNotContain("Wychwyć braki");
        assertThat(items(technical)).isEqualTo(3);
        assertThat(assortment + prices + technical).doesNotContain("??");
    }
}
