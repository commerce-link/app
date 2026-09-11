package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSectionFragmentTest {

    private static final Path FRAGMENT = Path.of("src/main/resources/templates/fragments/supplier-section.html");

    private String fragment() throws Exception {
        return Files.readString(FRAGMENT, StandardCharsets.UTF_8);
    }

    @Test
    void declaresTheSupplierSectionFragmentWithTheExpectedSignature() throws Exception {
        assertThat(fragment()).contains(
                "th:fragment=\"supplierSection(rows, manual, showMode, titleKey, addButtonId, "
                        + "addButtonLabelKey, addDisabled, addDisabledTitleKey, successMessage)\"");
    }

    @Test
    void wrapsTheHeadingAddButtonAndTableSoOneSwapRefreshesAllOfIt() throws Exception {
        String html = fragment();
        // the heading and Add button live inside the same fragment as the table, instead of being
        // static markup around a th:replace to just the table -- a single swap of this fragment's
        // output must refresh all three
        assertThat(html).contains("#{${titleKey}}");
        assertThat(html).contains("th:id=\"${addButtonId}\"");
        assertThat(html).contains("#{${addButtonLabelKey}}");
        assertThat(html).contains(
                "th:replace=\"~{fragments/supplier-table :: supplierTable(${rows}, ${manual}, ${showMode})}\"");
    }

    @Test
    void declaresNoArgumentWrapperFragmentsForTheAsyncEndpointsToReturnAsViewNames() throws Exception {
        // Spring's ThymeleafView rejects a controller-returned view name that carries positional
        // fragment parameters (only th:replace/th:insert may use them), so the async endpoints
        // return these no-argument selectors instead, with every value the fragment needs already
        // published on the model by SupplierSectionModel
        String html = fragment();
        assertThat(html).contains("th:fragment=\"externalSection\"");
        assertThat(html).contains("th:fragment=\"manualSection\"");
    }

    @Test
    void theWrapperFragmentsDelegateToTheParameterizedFragmentByReplacingThemselves() throws Exception {
        String html = fragment();
        // th:replace (not th:insert) so the rendered output IS the supplierSection div itself,
        // carrying data-success-message on its root -- the same shape the page script's
        // applySectionSwap expects to find on temp.firstElementChild
        assertThat(html).contains(
                "th:fragment=\"externalSection\"\n"
                        + "     th:replace=\"~{fragments/supplier-section :: supplierSection(${sectionRows}, false, "
                        + "${sectionShowMode}, 'store.supplier.section.title', 'supplier-add-button', "
                        + "'store.supplier.add.button', ${sectionAvailableSuppliers.isEmpty()}, "
                        + "'store.supplier.add.none', ${sectionSuccessMessage})}\">");
        assertThat(html).contains(
                "th:fragment=\"manualSection\"\n"
                        + "     th:replace=\"~{fragments/supplier-section :: supplierSection(${sectionRows}, true, "
                        + "false, 'store.manual.section.title', 'manual-add-button', 'store.manual.add.button', "
                        + "false, null, ${sectionSuccessMessage})}\">");
    }

    @Test
    void reusesTheExistingTableFragmentInsteadOfDuplicatingIt() throws Exception {
        assertThat(fragment()).doesNotContain("<tbody");
    }

    @Test
    void disablesTheAddButtonAndExplainsWhyOnlyWhenAskedTo() throws Exception {
        String html = fragment();
        assertThat(html).contains("th:disabled=\"${addDisabled}\"");
        assertThat(html).contains("th:title=\"${addDisabled} ? #{${addDisabledTitleKey}} : ''\"");
    }

    @Test
    void carriesTheSuccessMessageAsADataAttributeOnTheFragmentRoot() throws Exception {
        // there is no redirect left to flash a success message through, so it travels back on the
        // response body instead -- the page script reads it once after swapping the section in and
        // shows it as a toast, the same way the dropship validation fragment carries
        // data-fully-available
        assertThat(fragment()).contains("th:attr=\"data-success-message=${successMessage},data-show-mode=${showMode}\"");
    }

    @Test
    void carriesShowModeAsADataAttributeSoASettingsSaveElsewhereCanBeReadFreshOnTheNextOpen() throws Exception {
        // canUseGlobalSuppliers can now change from the fulfilment settings section, not just here;
        // the supplier modal (rendered once, outside this fragment) reads this attribute fresh on
        // every open instead of relying on a page-load snapshot that such a save would leave stale
        assertThat(fragment()).contains("data-show-mode=${showMode}");
    }

    @Test
    void definesASmallErrorFragmentForARejectedMutation() throws Exception {
        // a non-2xx status (set by the controller) lets the page script tell this apart from a
        // successful section swap
        String html = fragment();
        assertThat(html).contains("th:fragment=\"sectionError\"");
        assertThat(html).contains("class=\"notification is-danger\"");
        assertThat(html).contains("th:text=\"${errorMessage}\"");
    }
}
