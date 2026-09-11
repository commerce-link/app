package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoreFulfilmentTemplateTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/store-fulfilment.html");
    private static final Path LAYOUT = Path.of("src/main/resources/templates/layout.html");

    private String template() throws Exception {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    }

    private String layout() throws Exception {
        return Files.readString(LAYOUT, StandardCharsets.UTF_8);
    }

    @Test
    void postsTheSupplierModalToThePerSupplierEndpoint() throws Exception {
        assertThat(template()).contains("'/fulfilment/supplier'");
    }

    @Test
    void offersOnlySuppliersThatAreNotConnectedYet() throws Exception {
        // pins the dropdown actually iterating the list to produce options, not just
        // referencing availableSuppliers somewhere else (e.g. the add-button's disabled check)
        assertThat(template()).contains("th:each=\"name : ${availableSuppliers}\"");
        assertThat(template()).contains("th:value=\"${name}\" th:text=\"${name}\"");
    }

    @Test
    void bindsCredentialInputsToTheConfigurationMap() throws Exception {
        assertThat(template()).contains("'configuration[' + ${cf.key()} + ']'");
    }

    @Test
    void hidesTheModeSelectorForStoresWithoutGlobalSuppliers() throws Exception {
        assertThat(template()).contains("supplier-mode-wrap");
        assertThat(template()).contains("canUseGlobalSuppliers");
    }

    @Test
    void warnsBeforeSwitchingAnOwnConnectionToGlobal() throws Exception {
        // switching to global deletes the stored secret and the feed file
        assertThat(template()).contains("#{store.supplier.mode.warning}");
        assertThat(template()).contains("supplier-mode-warning");
    }

    @Test
    void keepsPasswordFieldsEmptyAndExplainsThatBlankMeansUnchanged() throws Exception {
        assertThat(template()).contains("#{store.supplier.password.keep}");
    }

    @Test
    void doesNotRenderTheOldCheckboxListAnyMore() throws Exception {
        String html = template();
        assertThat(html).doesNotContain("supplierSelections[");
        assertThat(html).doesNotContain("class=\"supplier-enabled\"");
    }

    @Test
    void doesNotRestoreASubmittedConfigurationAnyMore() throws Exception {
        // the modal no longer closes (or reloads) on a rejected save, so the operator's own input
        // is simply still sitting in the form; the flash-restore machinery this used to need is gone
        String html = template();
        assertThat(html).doesNotContain("submittedSupplierConfiguration");
        assertThat(html).doesNotContain("submittedIncludeInPricing");
        assertThat(html).doesNotContain("submittedIncludeInFulfilment");
        assertThat(html).doesNotContain("editSupplier");

        // credential inputs fall back to the stored configuration directly, as before the
        // restore machinery existed -- password fields still always render empty
        assertThat(html.replaceAll("\\s+", " ")).contains(
                "${cf.type().name() == 'PASSWORD' ? '' : (form.supplierConfiguration.get(entry.key) != null");
    }

    @Test
    void scopesTheConfigureButtonLookupToItsOwnSection() throws Exception {
        String html = template();
        String selector = "querySelectorAll('[data-configure-supplier]')";

        // each modal script looks up [data-configure-supplier] only within its own stable section
        // container (read fresh on every open/refresh, never from a document-wide query or a JS
        // array snapshotted at page load that would go stale after an async swap)
        String externalScoped = "externalSection." + selector;
        String manualScoped = "manualSection." + selector;
        assertThat(html).contains(externalScoped);
        assertThat(html).contains(manualScoped);

        // Every occurrence of the raw selector must be one of the two scoped forms above --
        // comparing the total count to the sum of the scoped counts catches a widened scope even
        // when the widened variant still contains "querySelectorAll('[data-configure-supplier]')"
        // as a substring (e.g. "document.querySelectorAll(...)"), which doesNotContain on the old,
        // narrower "document." + selector string would miss.
        int totalOccurrences = countOccurrences(html, selector);
        int scopedOccurrences = countOccurrences(html, externalScoped) + countOccurrences(html, manualScoped);
        assertThat(totalOccurrences).isGreaterThan(0);
        assertThat(totalOccurrences).isEqualTo(scopedOccurrences);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void disablesCredentialInputsByDefaultAndReenablesOnlyTheActiveProvider() throws Exception {
        String html = template();

        // rendered disabled by default: the fail-safe if the switching script never runs
        assertThat(html).contains("th:placeholder=\"${cf.placeholder()}\" disabled />");

        // the script re-enables inputs only within the active provider's own field group
        assertThat(html).contains(
                "group.querySelectorAll('input').forEach(function (input) { input.disabled = !active; });");
    }

    @Test
    void addsAManualSupplierWithItsNameAndFileInOneModal() throws Exception {
        String html = template();
        assertThat(html).contains("id=\"manualAddModal\"");
        assertThat(html).contains("id=\"manual-new-name\"");
        assertThat(html).contains("id=\"manual-add-file\"");
    }

    @Test
    void savesManualSupplierFlagsThroughItsOwnEndpoint() throws Exception {
        assertThat(template()).contains("'/fulfilment/manual-supplier/'");
    }

    @Test
    void disablesTheActiveFlagUntilAFileHasBeenUploaded() throws Exception {
        // applySelections forces enabled to false without a feed, so the checkbox must not pretend otherwise
        String html = template();
        assertThat(html).contains("#{store.manual.enable.needsfeed}");
        assertThat(html).contains("manual-enabled");

        // pins the actual disabling logic, not just the presence of the hint and the class:
        // hasFeed() never reaches this script (it does not survive Jackson serialisation on the
        // record), so the check must be driven by feedLastModified instead
        assertThat(html).contains("var fileUploaded = !!row.feedLastModified;");
        assertThat(html).contains("configEnabled.disabled = !fileUploaded;");
        assertThat(html).doesNotContain("row.hasFeed");
    }

    @Test
    void surfacesAnUploadFailureDuringCreationInsteadOfRefreshingAsIfItSucceeded() throws Exception {
        // the supplier create call and the follow-up file upload are two separate requests; a
        // server-rejected file (empty, unparseable rows) must not be swallowed by an unconditional
        // section refresh that would leave the operator believing the upload worked
        String normalized = template().replaceAll("\\s+", " ");

        // the response is parsed and gated on res.ok before anything else happens, exactly like
        // uploadForCurrent's proven pattern -- a plain ".then(function () { refreshManualSection()...; })"
        // right after the fetch, with no such gate, would not match this
        assertThat(normalized).contains(
                "fetch(basePath + '/manual-supplier/' + encodeURIComponent(identity) + '/feed', "
                        + "{ method: 'POST', body: data }) "
                        + ".then(function (r) { return r.json().then(function (j) { return { ok: r.ok, body: j }; }); }) "
                        + ".then(function (res) {");

        // create/uploadFeed stay JSON endpoints (they are already fetch-driven), so success is
        // followed by a GET that refreshes just the manual section instead of a page reload, and
        // the modal only closes once that refresh has actually landed; a dropped connection on the
        // upload call itself still reports an error rather than falling through to a refresh
        assertThat(normalized).contains(
                "if (!res.ok) { showError(addError, res.body.message); return; } "
                        + "refreshManualSection().then(function () { addModal.classList.remove('is-active'); }); }) "
                        + ".catch(function () { showError(addError); });");
    }

    @Test
    void neverReloadsThePageAnyMoreOnAnyMutation() throws Exception {
        // the whole point of this change: every connect/edit/disconnect/save/delete swaps its
        // section back in instead of a full page round trip
        assertThat(template()).doesNotContain("window.location.reload");
    }

    @Test
    void disablesTheSubmitButtonWhileARequestIsInFlightSoADoubleClickCannotSubmitTwice() throws Exception {
        String html = template();
        assertThat(html).contains("if (submitInFlight) { return; }");
        assertThat(html).contains("submitInFlight = true;");
        assertThat(html).contains("supplierSubmitButton.disabled = true;");
    }

    @Test
    void swapsTheExternalSectionInPlaceAndKeepsTheModalOpenOnAValidationFailure() throws Exception {
        String html = template();
        // success: the section is replaced and the modal closes
        assertThat(html).contains("applySectionSwap(externalSection, result.html);");
        assertThat(html).contains("refreshAvailableOptions();");
        // failure: a non-2xx response shows the fragment inside the modal instead, leaving the
        // operator's own input in the form untouched
        assertThat(html).contains("if (!result.ok) { showFormError(result.html); return; }");
    }

    @Test
    void recomputesTheAddDropdownFromTheFullSupplierListDataAttributeWithoutASecondRequest() throws Exception {
        String html = template();
        // the full provider list (connected or not) is rendered once as a data attribute on the
        // stable container, never touched by a section swap
        assertThat(html).contains("data-all-suppliers=${#strings.listJoin(allSupplierNames, ';')}");
        // and the connected identities are re-read from the freshly-swapped table instead of a
        // second fetch for the option list
        assertThat(html).contains("var all = (externalSection.dataset.allSuppliers || '').split(';')");
        assertThat(html).doesNotContain("/available-suppliers");
    }

    @Test
    void bindsTheAddButtonsThroughSectionDelegationSinceTheyAreInsideTheSwappedContent() throws Exception {
        // #supplier-add-button and #manual-add-button are rendered inside the section fragment
        // itself, so a direct addEventListener on the button (bound once at page load) would stop
        // working after the very first swap; delegating the click from the stable container instead
        // keeps working because the container itself is never replaced
        String html = template();
        assertThat(html).contains("event.target.closest('#supplier-add-button')");
        assertThat(html).contains("event.target.closest('#manual-add-button')");
        assertThat(html).doesNotContain("document.getElementById('supplier-add-button').addEventListener");
        assertThat(html).doesNotContain("document.getElementById('manual-add-button').addEventListener");
    }

    @Test
    void disconnectAndDeleteReportAFailureWithAnAlertRatherThanSwappingTheErrorFragmentIn() throws Exception {
        // unlike the modals (which have a slot to show the error inline), disconnect/delete have
        // no open dialog to show it in, so a rejected mutation falls back to an alert instead of
        // swapping the small error fragment into the live table
        String html = template();
        assertThat(html).contains("alert(extractErrorMessage(result.html, genericError));");
    }

    @Test
    void layoutFallsBackToTheDefaultConfirmationTextWhenNoMessageIsSupplied() throws Exception {
        // this pins layout.html rather than LayoutFlashMessagesTemplateTest: the property being
        // protected is exactly what this file's manual-delete and external-disconnect flows
        // (both wired through confirmDelete(button, callback) above) depend on, so the pin belongs
        // next to the feature it guards, not next to the unrelated flash-banner assertions
        String html = layout();
        String normalized = html.replaceAll("\\s+", " ");

        // the default text is captured once from the server-rendered paragraph, so it always
        // mirrors whatever #{confirm.delete} resolves to, in whichever locale
        assertThat(html).contains("<p id=\"deleteModalMessage\" th:text=\"#{confirm.delete}\">");
        assertThat(normalized).contains(
                "const defaultDeleteMessage = document.getElementById('deleteModalMessage').textContent;");

        // every screen that still calls confirmDelete(this) with no data-confirm-message must
        // keep seeing exactly that captured default; dropping the "|| defaultDeleteMessage" half
        // of this fallback (or reordering it) would leave those screens showing "undefined"
        assertThat(normalized).contains(
                "document.getElementById('deleteModalMessage').textContent = "
                        + "button.dataset.confirmMessage || defaultDeleteMessage;");

        // guards against the pre-fix shape reappearing: an id-less paragraph gives confirmDelete
        // no element to read the default from or swap the text of
        assertThat(html).doesNotContain("<p th:text=\"#{confirm.delete}\">");
    }
}
