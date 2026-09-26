package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SharedFragmentsTemplateTest {

    @Test
    void theRecordHeaderPutsTheStatusNextToTheTitleAndTheMetaBelow() {
        // given: the StringTemplateResolver's "*<*" resolvable pattern only matches a single-line candidate
        // (Thymeleaf's glob-to-regex translation does not set DOTALL), so the markup is one concatenated line
        String template = "<div th:replace=\"~{fragments/settings-header :: record('/dashboard/orders', 'Zamówienia', "
                + "'Zamówienie 3e373abc', ~{::status}, ~{::meta}, null)}\">"
                + "<span th:fragment=\"status\" class=\"cl-status is-warn\">W kompletacji</span>"
                + "<p th:fragment=\"meta\" class=\"cl-record-meta\"><span>Jan</span></p>"
                + "</div>";

        // when
        String html = SettingsTemplateRenderer.render(template, Map.of());

        // then
        assertThat(html).contains("class=\"cl-record-title\"").contains("<h1 class=\"cl-page-title\">Zamówienie 3e373abc</h1>")
                .contains("cl-status is-warn").contains("cl-record-meta").contains("href=\"/dashboard/orders\"");
        assertThat(html.indexOf("cl-status")).isLessThan(html.indexOf("cl-record-meta"));
    }
}
