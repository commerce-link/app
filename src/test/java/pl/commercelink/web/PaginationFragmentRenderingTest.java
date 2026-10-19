package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the pagination fragment with real Pagination records and the Polish bundle. */
class PaginationFragmentRenderingTest {

    private static final String FRAGMENT = "<div th:replace=\"~{fragments/pagination :: pages(${pagination})}\"></div>";

    @Test
    void openEndedRangeHasNoTotal() {
        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT,
                Map.of("pagination", Pagination.openEnded(2, 25, 25, true, n -> "/list?page=" + n)));

        // then
        assertThat(html).contains("<span class=\"cl-pagination-page\">26–50</span>").doesNotContain(" z ");
    }

    @Test
    void closedRangeKeepsTheTotal() {
        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT,
                Map.of("pagination", Pagination.of(2, 60, 25, n -> "/x?page=" + n)));

        // then
        assertThat(html).contains("26–50 z 60");
    }
}
