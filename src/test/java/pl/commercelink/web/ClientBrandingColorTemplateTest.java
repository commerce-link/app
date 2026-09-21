package pl.commercelink.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ClientBrandingColorTemplateTest {

    private static final Pattern STYLE_WITH_COLOR = Pattern.compile("th:style(?:append)?=\"([^\"]*safePrimaryColor[^\"]*)\"");

    @ParameterizedTest
    @ValueSource(strings = {"clientOffer.html", "clientOrder.html", "client-return.html"})
    void customerPagesWriteOnlyAValidatedBrandColourIntoStyles(String template) throws Exception {
        // given
        String html = Files.readString(Path.of("src/main/resources/templates", template), StandardCharsets.UTF_8);

        // when
        Matcher styles = STYLE_WITH_COLOR.matcher(html);

        // then
        assertThat(html).doesNotContain("branding.primaryColor");
        int count = 0;
        while (styles.find()) {
            count++;
            assertThat(styles.group(1)).as("style without an empty-colour guard: %s", styles.group(1))
                    .startsWith("${#strings.isEmpty(branding.safePrimaryColor)} ? null : ");
        }
        assertThat(count).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"clientOffer.html", "clientOrder.html", "client-return.html"})
    void layoutStylesDoNotDependOnWhetherTheStoreHasABrandColour(String template) throws Exception {
        // given
        String html = Files.readString(Path.of("src/main/resources/templates", template), StandardCharsets.UTF_8);

        // when
        Matcher styles = STYLE_WITH_COLOR.matcher(html);

        // then
        while (styles.find()) {
            assertThat(styles.group(1)).doesNotContain("padding").doesNotContain("margin").doesNotContain("width");
        }
    }
}
