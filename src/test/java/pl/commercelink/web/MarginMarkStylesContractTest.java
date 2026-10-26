package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the margin mark of an item without a cost is dimmed: the mark draws its own tooltip (::before/::after), so an
 * opacity on the mark would make the tooltip see-through as well; only the icon inside it is dimmed.
 */
class MarginMarkStylesContractTest {

    private static String css() throws Exception {
        return Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
    }

    @Test
    void anUnknownMarginDimsOnlyItsIconSoTheTooltipStaysOpaque() throws Exception {
        // given
        String css = css();

        // when
        int rule = css.indexOf(".cl-page .cl-margin.is-unknown > * {");

        // then
        assertThat(rule).as("the dimmed icon rule").isNotNegative();
        assertThat(css.substring(rule, css.indexOf('}', rule))).contains("opacity: .55;");
        assertThat(Pattern.compile("\\.cl-margin\\.is-unknown\\s*\\{[^}]*opacity").matcher(css).find())
                .as("no opacity on the mark itself").isFalse();
    }
}
