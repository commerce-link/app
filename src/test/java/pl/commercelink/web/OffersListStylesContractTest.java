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
    void clientNameStaysOnOneLineOnlyBetweenTheRailAndTheFullSidebar() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains("""
@media (min-width: 720px) and (max-width: 1215px) {
    .cl-page .cl-table.is-offers .cl-cell-client {
        display: block;
        max-width: 22ch;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
}""");
    }

    @Test
    void offerNameStaysOnOneLineOnlyBetweenTheRailAndTheFullSidebar() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains("""
@media (min-width: 720px) and (max-width: 1215px) {
    .cl-page .cl-table.is-offers .cl-cell-name {
        display: block;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
}""");
    }
}
