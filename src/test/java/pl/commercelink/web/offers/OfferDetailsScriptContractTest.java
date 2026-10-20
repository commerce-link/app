package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The offer details page scripts, read as source: the repository has no JavaScript test runner. */
class OfferDetailsScriptContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/" + path), StandardCharsets.UTF_8);
    }

    @Test
    void pageLoadsTheGuardTheTableAndThePageScripts() throws Exception {
        // when
        String page = read("templates/offers/details.html");

        // then
        assertThat(page).contains("@{/js/dirty-form.js}").contains("@{/js/offer-lines.js}").contains("@{/js/offer-details.js}");
    }

    @Test
    void addItemsDialogAsksBeforeItPostsAndStillPostsToItsAddress() throws Exception {
        // when
        String js = read("static/js/item-add-dialog.js");

        // then — a cancelable event the offer page uses to save its table first; the order page does not listen
        assertThat(js).contains("'cl:item-add-submit'").contains("cancelable: true").contains("proceed:")
                .contains("data-add-items-url").contains("form.submit()");
    }

    @Test
    void unsavedChangesGuardIsGeneric() throws Exception {
        // when
        String js = read("static/js/dirty-form.js");

        // then
        assertThat(js).contains("form[data-cl-dirty-form]").contains("window.CL_isDirty").contains("'cl:dirty-changed'")
                .contains("'beforeunload'").contains("event.defaultPrevented");
        assertThat(js.toLowerCase()).doesNotContain("offer").doesNotContain("basketitems").doesNotContain("data-cl-line");
    }

    @Test
    void tableRenumbersItemsAndCountsTotalsWithTheSharedFormatter() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");

        // then
        assertThat(js).contains("basketItems\\[\\d+\\]").contains("CL_formatMoney").contains("new Sortable(")
                .contains("'cl:lines-changed'").contains("prefers-reduced-motion");
    }

    @Test
    void pageSavesTheItemsBeforeAnActionAndRefreshesCardsWithoutLosingTheTable() throws Exception {
        // when
        String js = read("static/js/offer-details.js");

        // then — a dialog saved while the table is dirty must not reload (dialog.js would, with close-on-success)
        assertThat(js).contains("CL_isDirty")
                .contains("template[data-cl-offer-refresh]").contains("'cl:form-replaced'").contains("'cl:item-add-submit'")
                .doesNotContain("data-cl-dialog-close-on-success");
    }

    @Test
    void tableThatCannotBeSavedShowsItsFieldBeforeAnyDialogOpens() throws Exception {
        // when
        String js = read("static/js/offer-details.js");

        // then — the confirmation, the unsaved question and the add-items dialog open only for a table that can be
        // saved; a dialog closed after a refused save would hand the focus back to its opener and drop the message
        assertThat(js).contains("var field = isDirty() ? fieldToFix() : null;")
                .contains("[data-cl-dialog-open=\"item-add-dialog\"]")
                .contains("form.checkValidity() ? null : form.querySelector(':invalid')")
                .contains("form.reportValidity();");
        int click = js.indexOf("var field = isDirty() ? fieldToFix() : null;");
        assertThat(js.indexOf("confirmThen(element, actionUrl(element), true)", click)).isGreaterThan(click);
        assertThat(js.indexOf("ask(element);", click)).isGreaterThan(click);
        // a refusal after a dialog is open still shows the field, from the last dialog's close event
        assertThat(js).contains("open[open.length - 1].addEventListener('close', function () {")
                .doesNotContain("closeOpenDialogs");
    }

    @Test
    void saveAsTemplateAsksForTheOfferNameFirst() throws Exception {
        // when
        String js = read("static/js/offer-details.js");
        String header = read("templates/offers/details/header.html");

        // then — the old form refused it with offers.name.required; the template is named after the offer
        assertThat(header).contains("data-name-required=#{offers.details.action.saveAsTemplate.nameRequired}");
        assertThat(js).contains("event.target.closest('[data-cl-save-as-template]')")
                .contains("form.querySelector('input[type=hidden][name=\"name\"]')")
                .contains("name.value.trim() !== ''")
                .contains("showToast(button.getAttribute('data-name-required'), 'danger');")
                .contains("'#offer-terms-card [data-cl-dialog-open=\"offer-terms-dialog\"]'");
    }

    @Test
    void backgroundSaveCountsOnlyALandingBackOnTheOfferPage() throws Exception {
        // when
        String js = read("static/js/offer-details.js");

        // then — an expired session redirects to the login page, which must not pass for a saved table
        assertThat(js).contains("redirect: 'follow'").contains("new URL(response.url).pathname")
                .contains("new URL(form.action, window.location.href).pathname").doesNotContain("opaqueredirect");
    }

    @Test
    void actionWithItsOwnConfirmationAsksOnceAndSavesFromThatConfirmation() throws Exception {
        // when
        String js = read("static/js/offer-details.js");
        String page = read("templates/offers/details.html");

        // then — one dialog: the action's confirmation carries the note and the "Zapisz i …" button
        assertThat(page).contains("data-confirm-note=#{offers.details.unsaved.confirmNote}");
        assertThat(js).contains("getAttribute('data-confirm-note')").contains("confirmThen(element, actionUrl(element), true)")
                .contains("confirmForm.removeEventListener('submit', saveThenSend)")
                .contains("HTMLFormElement.prototype.submit.call(confirmForm)");
    }

    @Test
    void termsSavedWhileTheTableIsDirtyUpdateTheNameSaveAsTemplatePosts() throws Exception {
        // when
        String js = read("static/js/offer-details.js");
        String page = read("templates/offers/details.html");
        String guard = read("static/js/dirty-form.js");

        // then — the hidden copies follow the terms dialog and do not count as an unsaved change of the table
        assertThat(js).contains("saved.id === 'offer-terms-form'").contains("['name', 'fulfilmentType']")
                .contains("copy.value = source.value");
        assertThat(page).contains("name=\"name\" th:value=\"${p.recordName() ?: ''}\" data-cl-dirty-ignore")
                .contains("name=\"fulfilmentType\" th:value=\"${p.fulfilmentType() ?: ''}\" data-cl-dirty-ignore");
        assertThat(guard).contains("!control.hasAttribute('data-cl-dirty-ignore')");
    }

    @Test
    void peekFollowsTheSummarySaveButtonAtAnyWidth() throws Exception {
        // when
        String js = read("static/js/offer-details.js");
        String css = read("static/css/commercelink.css");

        // then — shown once the summary's button leaves the screen, hidden (not rendered) once it is clear of the edges, CSS alone below 1366 px without JS
        assertThat(js).contains("new IntersectionObserver(").contains("'#summary [data-cl-save-items]'")
                .contains("'data-cl-peek-state'").contains("set('shown');").contains("--cl-topbar-height");
        assertThat(js).contains("set('hidden');").contains("var BAND = 100;")
                .contains("rootMargin: '-' + (topbar + band) + 'px 0px -' + band + 'px 0px'")
                // the band covers the shown peek's own box (taller than 100 px on a phone, where Safari has no scroll anchoring)
                .contains("var needed = Math.ceil(Math.max(peek.getBoundingClientRect().height + gap, moved)) + 8;").contains("if (needed > band) {");
        // never hidden from under the focus (a display:none peek drops it to the body); re-checked when it leaves
        assertThat(js).contains("if (clear && !peek.contains(focused))").contains("hideUnless(document.activeElement);")
                .contains("peek.addEventListener('focusout'").contains("hideUnless(event.relatedTarget);");
        assertThat(css).contains(".cl-summary-peek.is-action[data-cl-peek-state=\"shown\"] { display: flex; }")
                .contains(".cl-summary-peek.is-action[data-cl-peek-state=\"hidden\"] { display: none; }")
                .contains("html:has(.cl-summary-peek.is-action[data-cl-peek-state=\"shown\"]) { scroll-padding-bottom: 80px; }");
    }

    @Test
    void backgroundSaveRefusedByAnInactiveStoreShowsTheRefusal() throws Exception {
        // when
        String js = read("static/js/offer-details.js");

        // then — "Zapisz i …" on a read-only dashboard said only that the save failed, not why
        assertThat(js).contains("response.status === 403 && /^text\\/plain/.test(response.headers.get('Content-Type') || '')")
                .contains("refused.reason = reason;")
                .contains("showToast((error && error.reason) || unsaved.getAttribute('data-failed'), 'danger');");
    }

    @Test
    void cleanPageMarksTheStoredVariantChoiceWhichTheServerTotalsCount() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");

        // then: the save's choice (without the dropped items) only once something changed and the totals are recounted
        assertThat(js).contains("var kept = shownAsStored ? each.items : each.items.filter(function (row) {")
                .contains("var shownAsStored = true;\n    refresh(false);")
                .contains("if (shownAsStored) {\n            shownAsStored = false;\n            refresh(false);");
    }

    @Test
    void totalIsAnnouncedOnlyWhenItChanged() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");

        // then: a move or a saved dialog recounts without changing the total
        assertThat(js).contains("var changedTotal = !shown || shown.textContent !== values.total;")
                .contains("if (changedTotal) {\n            announceTotal(values.total);");
    }

    @Test
    void confirmationClosedWhileItsSaveRanDoesNotRunTheAction() throws Exception {
        // when
        String js = read("static/js/offer-details.js");

        // then
        assertThat(js).contains("if (!confirmDialog.open) {\n                    window.location.reload();\n                    return;");
        assertThat(js.indexOf("if (!confirmDialog.open) {")).isLessThan(js.indexOf("var savedUrl = actionUrl(element);"));
    }

    @Test
    void contactNoteDescribesTheFieldsOfEveryRuleOfItsGroup() throws Exception {
        // when
        String js = read("static/js/form-check.js");

        // then: shown again after one rule is met, the note is named by the fields of the other rule too
        assertThat(js).contains("group.querySelectorAll(':scope > [data-cl-any-of]').forEach(function (each) {")
                .contains("describedBy(control, note.id, !note.hidden && each.hidden);");
        assertThat(js).contains("if (!form.hasAttribute('data-cl-async')) {\n            window.addEventListener('pageshow'");
    }

    @Test
    void draggedRowShowsOnceAsTheRowMovingThroughTheTable() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");
        String css = read("static/css/commercelink.css");

        // then: no browser copy at the pointer, no Sortable copy under a finger; the row in the table looks lifted
        assertThat(js).contains("setData: function (dataTransfer) {")
                .contains("dataTransfer.setDragImage(noDragImage, 0, 0);");
        assertThat(css).contains(".cl-page .cl-table.is-line-editor tr.sortable-fallback { visibility: hidden; }")
                .contains(".cl-page .cl-table.is-line-editor tr.sortable-ghost > * { background: var(--cl-accent-soft); box-shadow:");
    }
}
