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
        assertThat(template()).contains("${availableSuppliers}");
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
}
