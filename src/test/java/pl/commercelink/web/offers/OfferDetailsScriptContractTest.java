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
        assertThat(js).contains("redirect: 'manual'").contains("opaqueredirect").contains("CL_isDirty")
                .contains("template[data-cl-offer-refresh]").contains("'cl:form-replaced'").contains("'cl:item-add-submit'")
                .doesNotContain("data-cl-dialog-close-on-success");
    }
}
