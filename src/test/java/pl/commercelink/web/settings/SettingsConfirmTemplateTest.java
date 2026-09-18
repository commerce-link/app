package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsConfirmTemplateTest {

    @Test
    void confirmsADestructiveActionWithAPostAndAWayBack() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("backLabel", "Magazyn");
        variables.put("confirm", new ConfirmAction("Usunąć adres „Oddział Berlin”?", "Adres zniknie z podpowiedzi.",
                "Usuń adres", "/dashboard/store/warehouse/addresses/a-1/delete", "/dashboard/store/warehouse"));

        // when
        String html = SettingsTemplateRenderer.render("settings-confirm", variables);

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Usunąć adres „Oddział Berlin”?</h1>");
        assertThat(html).containsPattern("<form class=\"cl-form\" method=\"post\" action=\"/dashboard/store/warehouse/addresses/a-1/delete\">");
        assertThat(html).contains("<button type=\"submit\" class=\"cl-button is-danger\">Usuń adres</button>");
        assertThat(html).contains("href=\"/dashboard/store/warehouse\"").contains(">Anuluj<");
    }
}
