package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The page script and its CSS address exactly the hooks the template prints. */
class RmaNewScriptContractTest {

    private static String read(String path) throws IOException {
        try (InputStream in = RmaNewScriptContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theScriptUsesTheTemplatesHooksAndNoAlert() throws IOException {
        // given
        String js = read("static/js/rma-new.js");
        String template = read("templates/rma-new/parts.html") + read("templates/rma-new.html");

        // then
        for (String hook : new String[]{"data-cl-rma-new", "data-cl-rma-item", "data-cl-rma-select", "data-cl-rma-qty",
                "data-cl-rma-detail", "data-cl-rma-count", "data-cl-rma-pieces", "data-cl-rma-value", "rma-empty-help"}) {
            assertThat(js).as(hook).contains(hook);
            assertThat(template).as(hook).contains(hook);
        }
        assertThat(js).doesNotContain("alert(").doesNotContain("innerHTML");
    }

    @Test
    void theCssIsScopedToTheNewComponents() throws IOException {
        // given
        String css = read("static/css/commercelink.css");
        int start = css.indexOf("/* New RMA page");

        // then
        assertThat(start).isPositive();
        String block = css.substring(start);
        assertThat(block.lines().filter(l -> l.startsWith(".cl-page") || l.startsWith("    .cl-page")))
                .allMatch(l -> l.contains("is-rma-items") || l.contains("is-rma-orders") || l.contains("cl-rma-item-fields"));
    }
}
