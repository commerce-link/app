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
    void selectVariantMarksOnlyTheRowAndRecounts() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");

        // then
        assertThat(js).contains("data-cl-line-select").contains("selectVariant(");
        String body = js.substring(js.indexOf("function selectVariant(row) {"));
        body = body.substring(0, body.indexOf("\n    }\n"));
        assertThat(body).contains("'variantSelected'").contains("recount()").contains("changed()");
    }

    @Test
    void clearedCostInTheItemDialogReadsZeroOnLeavingTheField() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");

        // then: shown as 0 on blur, and stored as 0 by "Zastosuj" even without leaving the field
        assertThat(js).contains("input('unitCost').addEventListener('blur', function () {\n            if (this.value === '') {\n                this.value = '0';")
                .contains("field(row, 'unitCost').value = input('unitCost').value === '' ? '0' : input('unitCost').value;");
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
        assertThat(js.indexOf("if (!confirmDialog.open) {")).isLessThan(js.indexOf("HTMLFormElement.prototype.submit.call(confirmForm);"));
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
    void draggedRowFollowsThePointerAndLeavesAnEmptySlot() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");
        String css = read("static/css/commercelink.css");

        // then: Sortable's own copy follows the pointer (not the browser's drag image), solid and with the row's widths
        assertThat(js).contains("forceFallback: true,").contains("fallbackTolerance: 4,")
                .contains("holdWidths(event.item, true);").contains("holdWidths(event.item, false);")
                .contains("node.classList.contains('sortable-fallback')").contains("node.style.opacity = '1';")
                .doesNotContain("setDragImage");
        assertThat(css).contains(".cl-page .cl-table.is-line-editor tr.sortable-ghost > * > * { visibility: hidden; }")
                .contains(".cl-page .cl-table.is-line-editor tr.sortable-fallback { display: table; table-layout: fixed;")
                .contains("html.cl-dragging-rows, html.cl-dragging-rows * { cursor: grabbing; }");
    }

    @Test
    void theLiveRecountUsesTheEstimateWhenTheCostIsEmpty() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");
        String recountRow = js.substring(js.indexOf("function recountRow("), js.indexOf("function recount()"));
        String recount = js.substring(js.indexOf("function recount()"), js.indexOf("var totalTimer"));

        // then
        assertThat(js).contains("data-estimated-cost").doesNotContain("data-median-cost").contains("function costOf(")
                .contains("'margin-estimated'").contains("data-cl-sum-estimated");
        // client 2026-10-27: the cost and margin lines went, the tooltip carries them; red only at a margin of 0 or less
        assertThat(js).doesNotContain("'line-cost").doesNotContain("'line-margin").doesNotContain("data-cl-line-cost")
                .doesNotContain("is-low").contains("tone: profit <= 0 ? 'loss' : 'ok',");
        assertThat(recountRow).contains("+ '\\n\\n' + (mark.getAttribute('data-cl-market-text') || '')");
        assertThat(recountRow).contains("costOf(");
        assertThat(recount).contains("costOf(");
    }

    @Test
    void theRowMenuTogglesMergingOnTheInvoiceInTheDraft() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");
        String toggle = js.substring(js.indexOf("function toggleConsolidated("), js.indexOf("// --- before a save"));

        // then — written into the hidden field like any edit, so it waits for "Zapisz zmiany"
        assertThat(js).contains("target.hasAttribute('data-cl-line-consolidate')").contains("toggleConsolidated(row);");
        assertThat(toggle).contains("consolidated.value = consolidated.value === 'true' ? 'false' : 'true';")
                .contains("showConsolidated(row);").contains("changed();");
        assertThat(js).contains("message(on ? 'deconsolidate' : 'consolidate')")
                .doesNotContain("data-cl-mark-consolidated");
    }

    @Test
    void anEditedCodeIsRenderedAsTheCopyButtonTheServerDraws() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");
        String renderSub = js.substring(js.indexOf("function renderSub("), js.indexOf("function showConsolidated("));

        // then
        assertThat(renderSub).contains("copy.className = 'cl-copy-inline';").contains("copy.setAttribute('data-cl-copy', mfn);")
                .contains("code.className = 'cl-copy-text';").contains("wrap.className = 'cl-line-code';")
                .contains("message('copy-mfn', [mfn])").doesNotContain("cl-code-nowrap");
    }

    @Test
    void addingItemsPostsTheDraftAndSwapsTheTable() throws Exception {
        // when
        String js = read("static/js/offer-details.js");
        String dialog = read("static/js/item-add-dialog.js");
        String handler = js.substring(js.indexOf("addEventListener('cl:item-add-submit'"));
        handler = handler.substring(0, handler.indexOf("\n    });\n"));

        // then
        assertThat(handler).contains("event.preventDefault();").contains("addToDraft(detail.params)")
                .contains("detail.done()").contains("detail.abort()")
                .doesNotContain("proceed(").doesNotContain("form.submit").doesNotContain("saveItems(");
        assertThat(handler.indexOf("event.preventDefault();")).isLessThan(handler.indexOf("addToDraft("));
        assertThat(js).contains("+ '/items/draft'").contains("querySelectorAll('tbody[data-cl-lines]')")
                .contains("window.CL_offerLines.reinit(Array.prototype.map.call(fresh, function (section) {")
                .contains("document.importNode(section, true)")
                .contains("template[data-cl-draft-meta]").contains("'data-skipped'")
                .doesNotContain("data-add-label").doesNotContain("data-add-count");
        assertThat(dialog).contains("params: params").contains("done: () =>").contains("proceed: () => form.submit()");
    }

    @Test
    void removingARowDoesNotPost() throws Exception {
        // when
        String details = read("static/js/offer-details.js");
        String lines = read("static/js/offer-lines.js");
        String items = read("templates/offers/details/items.html");

        // then
        assertThat(items).contains("data-cl-line-remove").doesNotContain("offers.details.line.remove.confirm")
                .contains("data-msg-line-removed-draft=#{offers.details.line.removedDraft}")
                .contains("data-msg-undo=#{offers.details.line.undo}");
        assertThat(details).doesNotContain("remove-item").doesNotContain("savedIndex").doesNotContain("leavesNoItem");
        assertThat(lines).contains("hasAttribute('data-cl-line-remove')").contains("function removeRow(")
                .contains("keptWithout(row) === 0").contains("'line-removed-draft'").contains("'undo'")
                .doesNotContain("savedIndex");
        String removeRow = lines.substring(lines.indexOf("function removeRow("));
        removeRow = removeRow.substring(0, removeRow.indexOf("\n    }\n"));
        assertThat(removeRow).contains("row.remove()").contains("refresh(true)").contains("changed()")
                .contains("next.before(row)").doesNotContain("fetch(").doesNotContain("submit");
    }

    @Test
    void theTableCanBeSetUpAgainForAFreshBody() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");

        // then
        assertThat(js).contains("reinit: reinit").contains("function reinit(fresh)").contains("function setUp()")
                .contains("sortable.destroy()").contains("table.addEventListener('click'")
                .doesNotContain("body.addEventListener(");
    }

    @Test
    void rowsMoveOnlyWithinTheirSection() throws Exception {
        // when
        String js = read("static/js/offer-lines.js");
        String items = read("templates/offers/details/items.html");

        // then
        assertThat(items).contains("data-cl-lines data-cl-lines-section=\"products\"")
                .contains("data-cl-lines data-cl-lines-section=\"services\"").contains("data-cl-section-head");
        assertThat(js).contains("querySelectorAll('tbody[data-cl-lines]')").contains("'data-cl-lines-section'")
                // one Sortable per section, each in a group of its own that neither gives nor takes rows
                .contains("sortables = bodies.map(function (section)")
                .contains("group: { name: 'offer-lines-' + section.getAttribute('data-cl-lines-section'), pull: false, put: false }")
                .contains("draggable: 'tr:not([data-cl-section-head])'")
                .doesNotContain("new Sortable(body,");
        // the moves stop at the section's heading, a new group goes to the products and a removed row comes back home
        assertThat(js).contains("function previousRow(row)").contains("productsBody().append(head, createFoot())")
                .contains("parent.isConnected");
    }

    @Test
    void aConflictFallsBackToAnOrdinarySubmit() throws Exception {
        // when
        String js = read("static/js/offer-details.js");
        String guard = read("static/js/dirty-form.js");

        // then
        assertThat(js).contains("response.status === 409").contains("HTMLFormElement.prototype.submit.call(form)")
                .contains("[data-cl-offer-conflict]").contains("window.CL_markDirty(form)")
                .contains("alert.focus()").contains("alert.scrollIntoView");
        assertThat(guard).contains("window.CL_markDirty = function (form)").contains("state.forced");
    }

    @Test
    void aDialogSaveCarriesTheNewVersion() throws Exception {
        // when
        String js = read("static/js/offer-details.js");

        // then
        assertThat(js).contains("template.content.querySelector('[data-cl-offer-version]')")
                .contains("form.querySelector('input[name=\"version\"]')");
    }

    @Test
    void theToastCanCarryAnAction() throws Exception {
        // when
        String layout = read("templates/layout.html");
        String css = read("static/css/commercelink.css");

        // then
        assertThat(layout).contains("function showToast(message, type, action)").contains("cl-toast-action")
                .contains("action.run()").contains("const CL_TOAST_ACTION_MS = 5000;").doesNotContain("8000")
                .contains("button.type = 'button'");
        assertThat(css).contains(".cl-toast-action {").contains(".cl-toast-action:focus-visible");
    }
}
