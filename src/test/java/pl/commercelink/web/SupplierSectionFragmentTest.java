package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSectionFragmentTest {

    private static final Path FRAGMENT = Path.of("src/main/resources/templates/fragments/supplier-section.html");

    private String fragment() throws Exception {
        // normalize line endings so assertions on multi-line fragments don't depend on the
        // checkout's line-ending config (CRLF vs LF)
        return Files.readString(FRAGMENT, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void declaresTheSupplierSectionFragmentWithTheExpectedSignature() throws Exception {
        assertThat(fragment()).contains(
                "th:fragment=\"supplierSection(rows, manual, showMode, titleKey, addButtonId, "
                        + "addButtonLabelKey, addDisabled, addDisabledTitleKey, successMessage, "
                        + "storedConfigSuppliers, configurations)\"");
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
                "th:replace=\"~{fragments/supplier-table :: supplierTable(${rows}, ${manual}, ${showMode}, "
                        + "${configurations})}\"");
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
                        + "'store.supplier.add.none', ${sectionSuccessMessage}, "
                        + "${sectionSuppliersWithStoredConfig}, ${sectionConfigurations})}\">");
        assertThat(html).contains(
                "th:fragment=\"manualSection\"\n"
                        + "     th:replace=\"~{fragments/supplier-section :: supplierSection(${sectionRows}, true, "
                        + "false, 'store.manual.section.title', 'manual-add-button', 'store.manual.add.button', "
                        + "false, null, ${sectionSuccessMessage}, '', null)}\">");
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
        assertThat(fragment()).contains(
                "th:attr=\"data-success-message=${successMessage},data-show-mode=${showMode},"
                        + "data-suppliers-with-stored-config=${storedConfigSuppliers}\"");
    }

    @Test
    void carriesShowModeAsADataAttributeSoASettingsSaveElsewhereCanBeReadFreshOnTheNextOpen() throws Exception {
        // canUseGlobalSuppliers can now change from the fulfilment settings section, not just here;
        // the supplier modal (rendered once, outside this fragment) reads this attribute fresh on
        // every open instead of relying on a page-load snapshot that such a save would leave stale
        assertThat(fragment()).contains("data-show-mode=${showMode}");
    }

    @Test
    void carriesStoredConfigSuppliersAsADataAttributeSoRequirednessCanBeReadFreshOnTheNextOpen() throws Exception {
        // a save can be the very thing that puts a supplier into this set (see
        // StoreSupplierConnectionService.suppliersWithStoredConfiguration); the supplier modal
        // (rendered once, outside this fragment) is never re-rendered by that save, so its password
        // fields' required-ness must be re-derived from this fresh attribute on every open instead
        // of the server-rendered `required` attribute, which is frozen at the page load that first
        // built the modal markup
        assertThat(fragment()).contains("data-suppliers-with-stored-config=${storedConfigSuppliers}");
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

    /**
     * Thymeleaf only notices an arity mismatch at render time, and by then the response is half
     * written: the page comes back truncated with a 200, which is exactly how this was found (the
     * manual section of store-fulfilment.html still passed ten arguments after the fragment grew an
     * eleventh). A fragment-only test cannot see that, so this walks every call site in every
     * template and compares the argument count against the declared signature.
     */
    @Test
    void everyCallSiteOfTheSupplierFragmentsPassesTheDeclaredNumberOfArguments() throws Exception {
        // given / when / then
        assertArityMatchesEverywhere("supplierSection", FRAGMENT);
        assertArityMatchesEverywhere("supplierTable",
                Path.of("src/main/resources/templates/fragments/supplier-table.html"));
    }

    private void assertArityMatchesEverywhere(String fragmentName, Path declaringFile) throws Exception {
        String declaration = Files.readString(declaringFile, StandardCharsets.UTF_8);
        int declared = argumentCount(declaration, declaration.indexOf("th:fragment=\"" + fragmentName + "("));
        assertThat(declared).as("declared parameters of %s", fragmentName).isGreaterThan(0);

        List<String> checked = new ArrayList<>();
        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template, StandardCharsets.UTF_8);
                int at = html.indexOf(fragmentName + "(");
                while (at >= 0) {
                    // skip the declaration itself, which is what `declared` was read from
                    if (!html.startsWith("th:fragment=\"" + fragmentName + "(", at - ("th:fragment=\"").length())) {
                        assertThat(argumentCount(html, at))
                                .as("arguments passed to %s in %s", fragmentName, template)
                                .isEqualTo(declared);
                        checked.add(template.getFileName().toString());
                    }
                    at = html.indexOf(fragmentName + "(", at + 1);
                }
            }
        }
        assertThat(checked).as("call sites of %s", fragmentName).isNotEmpty();
    }

    /** Number of top-level arguments in the parenthesised list that starts at or after `from`. */
    private int argumentCount(String text, int from) {
        int open = text.indexOf('(', from);
        if (open < 0) {
            return 0;
        }
        int depth = 0;
        int arguments = 1;
        char quote = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (c) {
                case '\'' -> quote = c;
                case '(', '{', '[' -> depth++;
                case ')', '}', ']' -> {
                    depth--;
                    if (depth == 0) {
                        return arguments;
                    }
                }
                case ',' -> {
                    if (depth == 1) {
                        arguments++;
                    }
                }
                default -> {
                }
            }
        }
        throw new IllegalStateException("unbalanced argument list at " + from);
    }
}
