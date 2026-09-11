package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoreFulfilmentTemplateTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/store-fulfilment.html");

    private String template() throws Exception {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8);
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
    void reopensTheSupplierModalAfterAFailedValidation() throws Exception {
        assertThat(template()).contains("${editSupplier}");
        assertThat(template()).contains("${addSupplier}");
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
    void restoresSubmittedCredentialsOnlyForTheProviderBeingReopened() throws Exception {
        String normalized = template().replaceAll("\\s+", " ");

        // the value expression actually reads from the flash map of what was just submitted
        assertThat(normalized).contains("submittedSupplierConfiguration.get(cf.key())");

        // ...but only when the provider block being rendered is the one being reopened,
        // so a rejected submission for one provider can never leak into another provider's field
        assertThat(normalized).contains(
                "submittedSupplierConfiguration != null and (entry.key == editSupplier or entry.key == addSupplier)");

        // password fields short-circuit to '' before ever consulting the submitted map
        assertThat(normalized).contains(
                "${cf.type().name() == 'PASSWORD' ? '' : (submittedSupplierConfiguration");
    }

    @Test
    void restoresSubmittedPricingAndFulfilmentFlagsOnlyForTheProviderBeingReopened() throws Exception {
        String html = template();

        // sourced from the flash values the save endpoint carries back, never from a DOM read
        assertThat(html).contains("var submittedIncludeInPricing = /*[[${submittedIncludeInPricing}]]*/ null;");
        assertThat(html).contains("var submittedIncludeInFulfilment = /*[[${submittedIncludeInFulfilment}]]*/ null;");

        // only applied when reopening for the exact supplier the flash values belong to
        assertThat(html).contains("var reopenTarget = editSupplier || addSupplier;");
        assertThat(html).contains("identity === reopenTarget");

        // the checkbox state itself is set from the submitted flag, not the connection row,
        // whenever useSubmittedFlags applies
        assertThat(html).contains(
                "pricing.checked = useSubmittedFlags ? !!submittedIncludeInPricing : row.includeInPricing;");
        assertThat(html).contains(
                "fulfilment.checked = useSubmittedFlags ? !!submittedIncludeInFulfilment : row.includeInFulfilment;");
    }

    @Test
    void scopesTheConfigureButtonListenerToTheExternalSupplierSection() throws Exception {
        String html = template();

        // the ruling: only the external-supplier table's Configure buttons open this modal
        assertThat(html).contains("querySelectorAll('#external-supplier-section [data-configure-supplier]')");

        // guards against the scope being accidentally widened back (e.g. by the next task,
        // which wires up the manual table's own Configure buttons in this same file)
        assertThat(html).doesNotContain("querySelectorAll('[data-configure-supplier]')");
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
    void surfacesAnUploadFailureDuringCreationInsteadOfReloadingAsIfItSucceeded() throws Exception {
        // the supplier create call and the follow-up file upload are two separate requests; a
        // server-rejected file (empty, unparseable rows) must not be swallowed by an unconditional
        // reload that would leave the operator believing the upload worked
        String normalized = template().replaceAll("\\s+", " ");

        // the response is parsed and gated on res.ok before anything else happens, exactly like
        // uploadForCurrent's proven pattern -- a plain ".then(function () { window.location.reload(); })"
        // right after the fetch, with no such gate, would not match this
        assertThat(normalized).contains(
                "fetch(basePath + '/manual-supplier/' + encodeURIComponent(identity) + '/feed', "
                        + "{ method: 'POST', body: data }) "
                        + ".then(function (r) { return r.json().then(function (j) { return { ok: r.ok, body: j }; }); }) "
                        + ".then(function (res) {");

        // the reload happens only after the ok-check passes, and a dropped connection on this same
        // call reports an error rather than falling through to a reload
        assertThat(normalized).contains(
                "if (!res.ok) { showError(addError, res.body.message); return; } "
                        + "window.location.reload(); }) "
                        + ".catch(function () { showError(addError); });");
    }
}
