package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesStylesContractTest {

    private static String section() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int start = css.indexOf("/* --- Pending deliveries");
        assertThat(start).as("section marker").isNotNegative();
        int end = css.indexOf("/* ---", start + 10);
        return css.substring(start, end < 0 ? css.length() : end);
    }

    @Test
    void usesOnlyTheFramesBreakpointsAndNoOwnTones() throws Exception {
        // given
        String section = section();

        // when
        Matcher media = Pattern.compile("@media[^{]*\\((?:max|min)-width:\\s*(\\d+)px\\)").matcher(section);

        // then
        while (media.find()) {
            assertThat(media.group(1)).isIn("719", "1023", "1215", "720", "1024", "1216");
        }
        assertThat(section).doesNotContain("--cl-ok:").doesNotContain("--cl-bad:").doesNotContain("#")
                .doesNotContainPattern("(?m)^\\s*\\.cl-status\\s*\\{");
    }

    @Test
    void phoneCardsAndTouchTargetsAreStyled() throws Exception {
        // when
        String section = section();

        // then
        assertThat(section).contains(".cl-page .cl-table.is-pending tr.cl-row-main")
                .contains(".cl-row-toggle").contains("min-height: 44px").contains(".cl-ref")
                .contains(".cl-subtable").contains(".cl-narrow-only").contains("prefers-reduced-motion");
    }

    @Test
    void singleRowToolbarIsStyledInThePendingSectionOnly() throws Exception {
        // when
        String section = section();

        // then
        assertThat(section).contains(".cl-table-toolbar.is-single-row .cl-toolbar-search").contains(".cl-list-meta .cl-toolbar-clear")
                .doesNotContain(".cl-toolbar-toggle");
    }
}
