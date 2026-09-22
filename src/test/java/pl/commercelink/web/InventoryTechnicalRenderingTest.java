package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.inventory.GlobalFeedRow;
import pl.commercelink.web.inventory.RelativeTime;
import pl.commercelink.web.inventory.TechnicalInventoryView;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTechnicalRenderingTest {

    @Test
    void rendersPlatformSizesAndGlobalFeedLoadTimes() {
        // given
        Context context = new Context();
        context.setVariable("technical", new TechnicalInventoryView(120000, 80000, "taxonomy-merged-full.csv", 5400,
                List.of(new GlobalFeedRow("Elko", LocalDateTime.of(2026, 9, 14, 11, 55), new RelativeTime("inventory.time.minutes", 5)))));

        // when
        String html = EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{fragments/inventory-technical :: technical}\"></div>", context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-inventory-fragment=\"technical\"");
        assertThat(html).contains("Global inventory").contains("taxonomy-merged-full.csv").contains("PIM index");
        assertThat(html).contains("Elko").contains("5 min ago");
    }
}
