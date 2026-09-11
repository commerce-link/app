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
}
