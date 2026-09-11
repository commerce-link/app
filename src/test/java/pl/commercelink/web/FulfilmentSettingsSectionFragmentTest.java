package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FulfilmentSettingsSectionFragmentTest {

    private static final Path FRAGMENT = Path.of("src/main/resources/templates/fragments/fulfilment-settings-section.html");

    private String fragment() throws Exception {
        return Files.readString(FRAGMENT, StandardCharsets.UTF_8);
    }

    @Test
    void declaresTheFulfilmentSettingsSectionFragmentWithTheExpectedSignature() throws Exception {
        assertThat(fragment()).contains(
                "th:fragment=\"fulfilmentSettingsSection(settings, isSuperAdmin, successMessage, refreshExternalSuppliers)\"");
    }

    @Test
    void declaresANoArgumentWrapperFragmentForTheAsyncSaveEndpointToReturnAsAViewName() throws Exception {
        // Spring's ThymeleafView rejects a controller-returned view name that carries positional
        // fragment parameters, so the async save endpoint returns this no-argument selector
        // instead, with every value the fragment needs already published on the model by
        // FulfilmentSettingsSectionModel.renderSection
        assertThat(fragment()).contains("th:fragment=\"section\"");
        assertThat(fragment()).contains(
                "th:replace=\"~{fragments/fulfilment-settings-section :: fulfilmentSettingsSection("
                        + "${sectionSettings}, ${sectionIsSuperAdmin}, ${sectionSuccessMessage}, "
                        + "${sectionRefreshExternalSuppliers})}\"");
    }

    @Test
    void carriesTheSuccessMessageAndRefreshFlagAsDataAttributesOnTheFragmentRoot() throws Exception {
        // there is no redirect left to flash a success message through and only one fragment can
        // travel back per request, so both travel back as data attributes on the root: the page
        // script shows a toast for the first and, when the second is true, separately refreshes
        // the external suppliers section
        assertThat(fragment()).contains("data-success-message=${successMessage}");
        assertThat(fragment()).contains("data-refresh-external-suppliers=${refreshExternalSuppliers}");
    }

    @Test
    void carriesEveryEditableFieldAsItsOwnDataAttributeForTheEditModalToReadFresh() throws Exception {
        // the modal reads these fresh on every open (like the supplier/manual modals read a table
        // row's dataset) instead of a page-load snapshot that would go stale after a save
        String html = fragment();
        assertThat(html).contains("data-order-assembly-days=${settings.orderAssemblyDays}");
        assertThat(html).contains("data-order-realization-days=${settings.orderRealizationDays}");
        assertThat(html).contains("data-automated-fulfilment=${settings.automatedFulfilment}");
        assertThat(html).contains("data-default-fulfilment-type=${settings.defaultFulfilmentType}");
        // the two super-admin-only fields are additionally gated by isSuperAdmin (see
        // withholdsTheTwoSuperAdminOnlyDataAttributesForANonSuperAdmin below) -- nothing is
        // editable by a non-super-admin either way, but the markup must not even carry them
        assertThat(html).contains("data-can-use-global-suppliers=${isSuperAdmin} ? ${settings.canUseGlobalSuppliers} : null");
        assertThat(html).contains("data-inventory-cache-ttl-minutes=${isSuperAdmin} ? ${settings.inventoryCacheTtlMinutes} : null");
    }

    @Test
    void rendersEveryFieldReadOnlyInsteadOfAsAnInput() throws Exception {
        String html = fragment();
        assertThat(html).doesNotContain("<input");
        assertThat(html).doesNotContain("<select");
        assertThat(html).contains("id=\"fulfilment-settings-edit-button\"");
    }

    @Test
    void showsTheTwoSuperAdminOnlyRowsOnlyForASuperAdmin() throws Exception {
        String html = fragment();
        long superAdminGatedRows = html.lines()
                .filter(line -> line.contains("th:if=\"${isSuperAdmin}\""))
                .count();
        assertThat(superAdminGatedRows).isEqualTo(2);
        assertThat(html).contains("#{store.use.global.suppliers}");
        assertThat(html).contains("#{store.inventory.cache.ttl}");
    }
}
