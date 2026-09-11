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
                "th:fragment=\"supplierSection(rows, manual, showMode, basePath, titleKey, addButtonId, "
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
                "th:replace=\"~{fragments/supplier-table :: supplierTable(${rows}, ${manual}, ${showMode}, ${basePath})}\"");
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
        assertThat(fragment()).contains("th:attr=\"data-success-message=${successMessage}\"");
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
