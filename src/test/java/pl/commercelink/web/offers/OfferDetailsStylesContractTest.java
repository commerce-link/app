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

    @Test
    void phoneCardsKeepTheCardInsetAndTheGroupHeadOnOneLineWithItsMenu() throws Exception {
        // when
        String phone = block().substring(block().indexOf("@media screen and (max-width: 719px)"));

        // then — the cells drop their padding, so the row holds the card's side inset; the variant bar moves to the row
        assertThat(phone).contains("gap: 8px 10px; padding: 14px 16px; }")
                .contains("tr.cl-line-group { display: grid; grid-template-columns: minmax(0, 1fr) 44px; gap: 8px 10px; padding: 12px 16px; }")
                .contains("tr.cl-line-group > td.cl-cell-menu { grid-column: 2; grid-row: 1; }")
                .contains("tr.is-variant { box-shadow: inset 3px 0 0 var(--cl-accent); }")
                .contains(":is(.cl-input.is-qty, .cl-input.is-money) { padding-left: 6px; padding-right: 6px; }");
    }

    @Test
    void visibilitySentenceWrapsOnAPhone() throws Exception {
        // when
        String phone = block().substring(block().indexOf("@media screen and (max-width: 719px)"));

        // then — kept on one line from 720 px, at 320 px the sentence is wider than the card
        assertThat(phone).contains(".cl-visibility { white-space: normal; }");
    }
}
