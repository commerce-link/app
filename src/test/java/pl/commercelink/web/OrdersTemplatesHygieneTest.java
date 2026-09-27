package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Global rules for the redesigned order details templates: no inline style or behaviour, no Bulma look. */
class OrdersTemplatesHygieneTest {

    static final Path TEMPLATES = Path.of("src/main/resources/templates");
    static final List<String> SINGLE_FILES = List.of("orders/details.html", "orders/status.html", "orders/settings.html",
            "orders/parts.html", "orders/bulk-confirm.html");
    static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)([^>]*)>(.*?)</script>", Pattern.DOTALL);

    static List<Path> templates() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> details = Files.walk(TEMPLATES.resolve("orders/details"))) {
            details.filter(path -> path.toString().endsWith(".html")).forEach(files::add);
        }
        SINGLE_FILES.forEach(file -> files.add(TEMPLATES.resolve(file)));
        return files;
    }

    @Test
    void noOrdersTemplateCarriesInlineStylesHandlersOrScripts() throws IOException {
        for (Path file : templates()) {
            // given
            String html = Files.readString(file, StandardCharsets.UTF_8);

            // then
            assertThat(html).as(file.toString())
                    .doesNotContain("style=\"").doesNotContain("<style").doesNotContain("onclick=")
                    .doesNotContain("onchange=").doesNotContain("oninput=").doesNotContain("onsubmit=")
                    .doesNotContain("confirm(").doesNotContain("alert(").doesNotContain("enumI18n")
                    .doesNotContain("${@");
            Matcher script = INLINE_SCRIPT.matcher(html);
            while (script.find()) {
                // only data may be inlined (th:inline="javascript" with variables), never behaviour
                assertThat(script.group(1)).as(file + " inline script").contains("th:inline=\"javascript\"");
                assertThat(script.group(2)).as(file + " inline script body").doesNotContain("function").doesNotContain("=>");
            }
        }
    }

    @Test
    void noOrdersTemplateUsesTheBulmaLook() throws IOException {
        for (Path file : templates()) {
            // given
            String html = Files.readString(file, StandardCharsets.UTF_8);

            // then
            assertThat(html).as(file.toString())
                    .doesNotContain("class=\"button").doesNotContain("class=\"box").doesNotContain("class=\"modal")
                    .doesNotContain("class=\"notification").doesNotContain("class=\"tag").doesNotContain("class=\"select")
                    .doesNotContain("class=\"input").doesNotContain("class=\"table").doesNotContain("is-link\"");
        }
    }

    @Test
    void theOldOrderDetailsTemplateIsGone() {
        // then
        assertThat(TEMPLATES.resolve("orderDetails.html")).doesNotExist();
    }
}
