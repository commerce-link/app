package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The offer details styles live in their own block of commercelink.css, on the design tokens only. */
class OfferDetailsStylesContractTest {

    private static final String START = "/* --- Offer details";

    private static String block() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));
        int start = css.indexOf(START);
        assertThat(start).isNotNegative();
        int end = css.indexOf("/* --- ", start + START.length());
        return end < 0 ? css.substring(start) : css.substring(start, end);
    }

    @Test
    void blockDefinesTheLineEditorTheUnsavedStateAndThePeekWithAnAction() throws Exception {
        // when
        String block = block();

        // then
        assertThat(block).contains(".cl-table.is-line-editor").contains(".cl-line-hint").contains(".cl-unsaved")
                .contains(".cl-summary-peek.is-action").contains("@media screen and (max-width: 719px)")
                .contains("@media screen and (max-width: 1023px)");
    }

    @Test
    void blockUsesOnlyDesignTokensForColoursAndNoImportant() throws Exception {
        // when
        String block = block();

        // then
        assertThat(block).doesNotContainPattern("#[0-9a-fA-F]{3,8}\\b").doesNotContain("rgb(").doesNotContain("rgba(")
                .doesNotContain("hsl(").doesNotContain("!important");
    }
}
