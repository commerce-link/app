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
                .contains("rootMargin: '-' + (topbar + BAND) + 'px 0px -' + BAND + 'px 0px'");
        // never hidden from under the focus (a display:none peek drops it to the body); re-checked when it leaves
        assertThat(js).contains("if (clear && !peek.contains(focused))").contains("hideUnless(document.activeElement);")
                .contains("peek.addEventListener('focusout'").contains("hideUnless(event.relatedTarget);");
        assertThat(css).contains(".cl-summary-peek.is-action[data-cl-peek-state=\"shown\"] { display: flex; }")
                .contains(".cl-summary-peek.is-action[data-cl-peek-state=\"hidden\"] { display: none; }")
                .contains("html:has(.cl-summary-peek.is-action[data-cl-peek-state=\"shown\"]) { scroll-padding-bottom: 80px; }");
    }
}
