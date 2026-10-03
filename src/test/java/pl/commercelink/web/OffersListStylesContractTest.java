package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The offers list styles live in commercelink.css and keep the row menu above the row link. */
class OffersListStylesContractTest {

    @Test
    void actionsColumnSitsAboveTheRowLink() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains(".cl-page .cl-table.is-offers .cl-table-actions")
                .contains("z-index: 1")
                .contains(".cl-page .cl-menu-item .cl-menu-desc")
                .contains(".cl-page .cl-table.is-offers .cl-copy-quick");
    }

    @Test
    void cellWithAnOpenMenuRisesAboveTheNextRowsCell() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains(".cl-page .cl-table.is-offers .cl-table-actions:has(.cl-menu[open])");
    }

    @Test
    void neutralValidityNoteKeepsAaContrastOnTheHoverRow() throws Exception {
        // given — --cl-ink-3 is 4.48:1 on the hover row's --cl-surface-2; the shared is-orders rule leaves .cl-due-note out
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains(".cl-page .cl-table.is-offers tbody tr:hover .cl-due-note:not(.is-warn) {\n    color: var(--cl-ink-2);")
                .contains(".cl-page .cl-table.is-offers .cl-due-note:not(.is-warn) {\n    font-weight: 400;");
    }

    @Test
    void outcomeNoticeUsesTheSharedAlertSpacing() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).doesNotContain("cl-page-notice");
    }

    @Test
    void phoneCardPutsTheMenuOnTheValidityNoteLine() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains("    .cl-page .cl-table.is-offers .cl-due-note {\n        grid-row: 5;")
                .contains("    .cl-page .cl-table.is-offers tr:has(.cl-status) .cl-table-actions {\n        grid-row: 5;");
    }

    @Test
    void copyScriptRevealsClipboardOnlyControlsAlsoAfterAListSwap() throws Exception {
        // given
        String js = Files.readString(Path.of("src/main/resources/static/js/copy-field.js"));

        // then
        assertThat(js).contains("[data-cl-copy-reveal][hidden]").contains("'cl-list:swapped'").contains("revealClipboardControls(document)");
    }

    @Test
    void copyScriptCopiesAnyButtonWithACopyValue() throws Exception {
        // given
        String js = Files.readString(Path.of("src/main/resources/static/js/copy-field.js"));

        // then
        assertThat(js).contains("closest('button[data-cl-copy]')");
    }

    @Test
    void namesShrinkWithAnEllipsisInsteadOfWideningTheTableBetweenTheRailAndTheFullSidebar() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then — width 0 keeps one-line names out of the columns' minimum; the other columns shrink to their content
        assertThat(css).contains("""
@media (min-width: 720px) and (max-width: 1215px) {
    .cl-page .cl-table.is-offers :is(.cl-cell-name, .cl-cell-client) {
        display: block;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        width: 0;
        min-width: 100%;
    }

    .cl-page .cl-table.is-offers :is(th, td):has(> :is(.cl-cell-name, .cl-cell-client)) {
        min-width: 7em;
    }

    .cl-page .cl-table.is-offers:not(.is-templates) tbody tr > :not(:has(> :is(.cl-cell-name, .cl-cell-client))) {
        width: 1%;
        white-space: nowrap;
    }
}""").doesNotContain("max-width: 22ch");
    }

    @Test
    void compactRowFollowsTheCardBesideTheOpenSidebar() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then — at 1216 px the card is 790 px; from about 1276 px (card 850 px) the full row fits
        assertThat(css).contains("""
.cl-page .cl-card:has(.cl-table.is-offers) {
    container-type: inline-size;
}

@media (min-width: 1216px) {
    @container (max-width: 849px) {
        .cl-page .cl-table.is-offers .cl-copy-quick,
        .cl-page .cl-table.is-offers .cl-offer-items,
        .cl-page .cl-table.is-offers .cl-who {
            display: none;
        }

        .cl-page .cl-table.is-offers :is(.cl-cell-name, .cl-cell-client) {
            display: block;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            width: 0;
            min-width: 100%;
        }
""");
    }

    @Test
    void drawerMovesTheCreationDateUnderTheIdAndDropsTheNetLine() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains("""
.cl-page .cl-table.is-offers .cl-narrow-only {
    display: none;
}

@media (min-width: 720px) and (max-width: 1023px) {
    .cl-page .cl-table.is-offers .is-secondary-column,
    .cl-page .cl-table.is-offers .cl-cell-net {
        display: none;
    }

    .cl-page .cl-table.is-offers .cl-narrow-only {
        display: inline;
    }
}""");
    }
}
