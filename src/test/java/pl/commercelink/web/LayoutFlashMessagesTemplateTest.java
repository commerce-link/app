package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Flash messages are rendered once, by the layout; pages decorated with it must not add their own copy. */
class LayoutFlashMessagesTemplateTest {

    private static final List<String> LAYOUT_PAGES = List.of("deliveries", "deliveryDetails", "dropshipCreate",
            "dropshipConfirmation", "deliveryPurchaseConfirmation", "store-categories", "store-copy");

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + template + ".html"), StandardCharsets.UTF_8);
    }

    @Test
    void layoutRendersEveryFlashKindOnce() throws Exception {
        // when
        String layout = read("layout");

        // then
        assertThat(layout).contains("<div th:if=\"${successMessage}\" class=\"notification is-success is-small has-text-centered\">");
        assertThat(layout).contains("<div th:if=\"${errorMessage}\" class=\"notification is-danger is-small has-text-centered\">");
        assertThat(layout).contains("<div th:if=\"${warningMessage}\" class=\"notification is-warning is-small has-text-centered\">");
    }

    @Test
    void pagesDecoratedWithTheLayoutDoNotRepeatTheFlashBanner() throws Exception {
        for (String page : LAYOUT_PAGES) {
            // when
            String html = read(page);

            // then
            assertThat(html).as(page).contains("layout:decorate=\"~{layout}\"");
            assertThat(html).as(page).doesNotContain("th:if=\"${errorMessage}\"");
            assertThat(html).as(page).doesNotContain("th:if=\"${successMessage}\"");
        }
    }
}
