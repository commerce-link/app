package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thymeleaf adds the CSRF field only to forms with th:action, so a POST form without it has to carry the field itself,
 * or the dashboard refuses it with a login redirect. A script building a form must take the token from window.clCsrf.
 */
class CsrfTokenInFormsTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path SCRIPTS = Path.of("src/main/resources/static/js");
    private static final Pattern FORM_TAG = Pattern.compile("<form\\b([^>]*)>", Pattern.DOTALL);
    // a method set through th:attr is a POST form as well: Thymeleaf only adds the token for th:action
    private static final Pattern POST_METHOD = Pattern.compile(
            "method\\s*=\\s*\"post\"|th:attr\\s*=\\s*\"[^\"]*\\bmethod\\s*=[^\"]*'post'", Pattern.CASE_INSENSITIVE);

    @Test
    void everyPostFormWithoutThActionCarriesTheTokenField() throws IOException {
        // given
        List<String> withoutToken = new ArrayList<>();

        // when
        for (Path template : files(TEMPLATES, ".html")) {
            String html = Files.readString(template);
            Matcher form = FORM_TAG.matcher(html);
            while (form.find()) {
                String attributes = form.group(1);
                if (!POST_METHOD.matcher(attributes).find() || attributes.contains("th:action")) {
                    continue;
                }
                int end = html.indexOf("</form>", form.end());
                String body = html.substring(form.end(), end < 0 ? html.length() : end);
                if (!body.contains("_csrf")) {
                    int line = (int) html.substring(0, form.start()).chars().filter(c -> c == '\n').count() + 1;
                    withoutToken.add(TEMPLATES.relativize(template) + ":" + line);
                }
            }
        }

        // then
        assertThat(withoutToken)
                .as("POST forms without th:action need <input type=\"hidden\" name=\"_csrf\" th:value=\"${_csrf.token}\"/>")
                .isEmpty();
    }

    @Test
    void everyScriptBuildingAFormTakesTheTokenFromTheLayout() throws IOException {
        // given
        List<String> withoutToken = new ArrayList<>();

        // when
        for (Path script : files(SCRIPTS, ".js")) {
            String source = Files.readString(script);
            if (source.contains("createElement('form')") && !source.contains("clCsrf")) {
                withoutToken.add(SCRIPTS.relativize(script).toString());
            }
        }

        // then
        assertThat(withoutToken)
                .as("scripts creating a form must call window.clCsrf.appendTo(form)")
                .isEmpty();
    }

    private static List<Path> files(Path root, String extension) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(extension)).sorted().toList();
        }
    }
}
