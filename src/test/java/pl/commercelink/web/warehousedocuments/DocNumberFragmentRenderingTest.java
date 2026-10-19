package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocNumberFragmentRenderingTest {

    private static final String PARTS = "<p><th:block th:replace=\"~{fragments/doc-number :: parts(${n})}\"></th:block></p>";

    @Test
    void numberBreaksOnlyAfterEachSlashAndCopiesAsThePlainNumber() {
        // when
        String html = SettingsTemplateRenderer.render(PARTS, Map.of("n", "PZ/MAG-uma2dqukxr/2026/000214"));

        // then
        assertThat(html).contains("<p>PZ/<wbr>MAG-uma2dqukxr/<wbr>2026/<wbr>000214</p>")
                .doesNotContain("​").doesNotContain("&#8203;");
    }

    @Test
    void textWithoutSlashOrEmptySegmentsStaysAsItIs() {
        // when
        String plain = SettingsTemplateRenderer.render(PARTS, Map.of("n", "Dostawa 3f2a9c1e"));
        String edges = SettingsTemplateRenderer.render(PARTS, Map.of("n", "/MAG1/"));

        // then
        assertThat(plain).contains("<p>Dostawa 3f2a9c1e</p>");
        assertThat(edges).contains("<p>/<wbr>MAG1/<wbr></p>");
    }

    @Test
    void escapesMarkupInTheNumber() {
        // when
        String html = SettingsTemplateRenderer.render(PARTS, Map.of("n", "<b>/x"));

        // then
        assertThat(html).contains("<p>&lt;b&gt;/<wbr>x</p>");
    }

    @Test
    void nullRendersNothing() {
        // given
        Map<String, Object> model = new HashMap<>();
        model.put("n", null);

        // when
        String html = SettingsTemplateRenderer.render(PARTS, model);

        // then
        assertThat(html).contains("<p></p>");
    }
}
