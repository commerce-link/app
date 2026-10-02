package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Printing several order cards from the orders list: the style and script contracts the feature relies on. */
class OrderCardsBulkPrintContractTest {

    static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    static String css() throws Exception {
        return read("src/main/resources/static/css/commercelink.css");
    }

    /** The declarations of every rule of the style sheet whose selector list is exactly {@code selector}, joined. */
    static String rule(String css, String selector) {
        StringBuilder body = new StringBuilder();
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(selector) + " \\{([^}]*)}").matcher(css);
        while (matcher.find()) {
            body.append(matcher.group(1));
        }
        return body.toString();
    }

    /**
     * The printed QR code and the one-column details hang off each card's own box, so every card of a batch has the
     * code at its own top-right corner. The declarations are the ones the page body carried, and the single card's box
     * is the same as the page body's, so the single card prints exactly as before.
     */
    @Test
    void theQrCodeAndTheOneColumnDetailsBelongToEachCard() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card:has(.cl-print-qr)")).contains("position: relative;");
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card:has(.cl-print-qr) dl.cl-kv.cl-print-meta"))
                .contains("grid-template-columns: minmax(0, 1fr);").contains("padding-right: 34mm;")
                .contains("align-content: start;");
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card:has(.cl-print-qr) .cl-kv.cl-print-meta dd"))
                .contains("overflow-wrap: break-word;");
        assertThat(rule(css, ".cl-page.cl-print-page .cl-page-header-row div.cl-print-qr"))
                .contains("position: absolute;").contains("top: 0;").contains("right: 0;").contains("width: 30mm;");
        assertThat(css).doesNotContain(".cl-page-body:has(.cl-print-qr)");
        assertThat(rule(css, ".cl-page .cl-print-card")).isEmpty();
    }

    /** A forced break keeps the margin after it, so the screen gap between cards is dropped on paper. */
    @Test
    void everyCardAfterTheFirstStartsOnANewPage() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card + .cl-print-card"))
                .contains("break-before: page;").contains("margin-top: 0;");
        assertThat(rule(css, ".cl-page .cl-print-card + .cl-print-card")).contains("margin-top: 40px;");
    }
}
