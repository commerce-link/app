package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** rma-detail.js and the page's CSS block address exactly the hooks the items card and its dialogs print. */
class RmaDetailScriptContractTest {

    private static String read(String path) throws IOException {
        try (InputStream in = RmaDetailScriptContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theScriptUsesTheTemplatesHooksAndNoAlert() throws IOException {
        // given
        String js = read("static/js/rma-detail.js");
        String templates = read("templates/rma/detail/items.html") + read("templates/rma/detail/dialogs.html");

        // then
        for (String hook : new String[]{"rmaItemsForm", "data-rma-id", "data-cl-select-row", "data-status", "data-qty",
                "data-name", "data-total", "data-id", "data-cl-rma-action", "data-accepts", "data-single-min-qty",
                "data-reason", "data-cl-rma-dialog", "data-cl-selection-list", "data-cl-rma-title", "data-template-one",
                "data-template-few", "data-template-many", "data-cl-dialog-submit", "data-cl-rma-condition",
                "data-cl-field-error", "split-qty1", "data-cl-rma-qty2", "data-cl-rma-split-name", "data-cl-rma-sum",
                "data-cl-rma-enable", "data-skip-", "data-qty-template", "data-skipped-template", "data-cl-enter-guard",
                "data-cl-rma-required", "data-cl-rma-counter", "data-cl-rma-add", "data-max-qty", "data-unit-price",
                "data-cl-rma-add-max", "data-cl-rma-add-effect", "data-cl-rma-split-form"}) {
            assertThat(js).as("script: " + hook).contains(hook);
            assertThat(templates).as("templates: " + hook).contains(hook);
        }
        assertThat(templates).contains("name=\"qty2\"").contains("name=\"qty1\"").contains("checkSerialNumbers");
        assertThat(js).contains("/items/").contains("/split");
        assertThat(js).doesNotContain("alert(").doesNotContain("innerHTML").doesNotContain("onclick");
        // a string replacement would read "$&" in an item name as a pattern; placeholders go through a function
        assertThat(js).doesNotContainPattern("\\.replace\\('\\{");
        assertThat(templates).doesNotContain("onclick").doesNotContain("onchange").doesNotContain("style=")
                .doesNotContain("<script");
    }

    @Test
    void theCssIsScopedToTheRmaDetailComponents() throws IOException {
        // given
        String css = read("static/css/commercelink.css");
        int start = css.indexOf("/* RMA details page");
        // the block is closed by its own marker: other screens' blocks may follow it after a merge
        int end = css.indexOf("/* End of RMA details page */", start);

        // then
        assertThat(start).isPositive();
        assertThat(end).isGreaterThan(start);
        String block = css.substring(start, end);
        assertThat(block.lines().filter(l -> l.startsWith(".cl-page") || l.startsWith("    .cl-page")))
                .isNotEmpty()
                .allMatch(l -> l.contains("is-rma-detail"));
    }
}
