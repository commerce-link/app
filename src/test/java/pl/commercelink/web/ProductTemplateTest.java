package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductAvailabilityType;
import pl.commercelink.products.ProductCustomAttribute;
import pl.commercelink.products.ProductCustomAttributeFilter;
import pl.commercelink.web.dtos.ProductForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/product.html"), StandardCharsets.UTF_8);
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void savesWithoutReloadAndAsksTheServerAgainOnAnError() throws Exception {
        // when / then
        assertThat(page()).contains("th:fragment=\"productForm\"").contains("id=\"product-form\"").contains("data-cl-async")
                .contains("data-cl-redirect=${redirectTo}").contains("@{/js/async-form.js}")
                .contains("errorSummary('product-errors'");
    }

    @Test
    void theWayThePriceIsSetIsRadiosAndTheOfferFieldsFollowThem() throws Exception {
        // given
        String page = page();

        // then
        assertThat(page).contains("data-cl-variant-select=\"availability\"")
                .contains("@{/js/variant-fields.js}");
        assertThat(occurrences(page, "data-cl-variant-when=\"availability=BasedOnSupply\"")).isEqualTo(3);
    }

    @Test
    void theRarelyUsedSectionsAreTwoDisclosuresOpenedByTheServer() throws Exception {
        // given
        String page = page();

        // then
        assertThat(occurrences(page, "cl-disclosure cl-card-extras")).isEqualTo(2);
        assertThat(page).contains("id=\"product-extras-stock\"").contains("th:open=\"${openStock}\"")
                .contains("id=\"product-extras-client\"").contains("th:open=\"${openClient}\"");
    }

    @Test
    void everyRepeatedListCarriesATemplateANoscriptSpareAndItsScript() throws Exception {
        // given
        String page = page();

        // then
        assertThat(page).contains("data-cl-repeat=\"quickFilters\"").contains("data-cl-repeat=\"customAttributes\"")
                .contains("data-cl-repeat=\"customAttributesFilters\"").contains("data-cl-repeat=\"metadata\"")
                .contains("@{/js/repeat-fields.js}");
        assertThat(occurrences(page, "data-cl-repeat-template")).isEqualTo(4);
        assertThat(occurrences(page, "<noscript>")).isEqualTo(4);
    }

    /**
     * The category of a custom filter is an internal grouping of filters and attributes ("GPU", "Cooler"), not a
     * product category, so it is a free text field rather than the PIM category picker.
     */
    @Test
    void theCustomFilterCategoryIsAFreeTextField() throws Exception {
        // when
        String html = rendered(true, form -> form.getCustomAttributesFilters().getFirst().setCategory("GPU"));

        // then
        assertThat(page()).doesNotContain("category-picker");
        int id = html.indexOf("id=\"customAttributeFilter-0-category\"");
        String field = html.substring(html.lastIndexOf("<input", id), html.indexOf(">", id));
        assertThat(field).contains("type=\"text\"").contains("name=\"customAttributesFilters[0].category\"")
                .contains("value=\"GPU\"");
        assertThat(html).contains("<label class=\"cl-label\" for=\"customAttributeFilter-0-category\">Filter category</label>");
    }

    @Test
    void theFlagsAreCheckboxesAndTheDeletionIsConfirmed() throws Exception {
        // when / then
        assertThat(page()).contains("settings-form :: check('enabled'").contains("settings-form :: check('service'")
                .contains("data-cl-confirm").contains("confirm-dialog :: dialog").contains("@{/js/confirm-dialog.js}");
    }

    @Test
    void carriesNoInlineStyleNoTableAndNoTooltip() throws Exception {
        // given
        String page = page();

        // then
        assertThat(page).doesNotContain("style=").doesNotContain("<table").doesNotContain("cl-tooltip")
                .doesNotContain("is-link").doesNotContain("notification");
    }

    /** The page as the controller renders it for a saved product with one attribute and one custom filter. */
    private static String rendered(boolean edit) {
        return rendered(edit, Map.of());
    }

    private static String rendered(boolean edit, Map<String, String> errors) {
        return rendered(edit, errors, List.of(new CatalogProductsController.LeadPart("EAN 4719331361600", false),
                new CatalogProductsController.LeadPart("PIM pim-1", false)));
    }

    private static String rendered(boolean edit, Map<String, String> errors, List<CatalogProductsController.LeadPart> lead) {
        return rendered(edit, errors, lead, form -> {
        });
    }

    /** The same page with the form changed first, as a product prefilled or saved with other values would give it. */
    private static String rendered(boolean edit, Consumer<ProductForm> change) {
        return rendered(edit, Map.of(), List.of(new CatalogProductsController.LeadPart("EAN 4719331361600", false)), change);
    }

    private static String rendered(boolean edit, Map<String, String> errors, List<CatalogProductsController.LeadPart> lead,
                                   Consumer<ProductForm> change) {
        Product product = new Product("k1", "pim-1", "4719331361600", "GV-N5080", "Gigabyte", "RTX 5080",
                "Gigabyte RTX 5080", "Default");
        product.setProductId("p1");
        ProductCustomAttribute attribute = new ProductCustomAttribute();
        attribute.setName("chipset");
        attribute.setValue("GB203");
        product.getCustomAttributes().add(attribute);
        ProductCustomAttributeFilter filter = new ProductCustomAttributeFilter();
        filter.setCategory("Płyty główne");
        filter.setName("Socket");
        filter.setValue("AM5");
        filter.setOperator("=");
        product.getCustomAttributesFilters().add(filter);
        ProductForm form = ProductForm.from(product);
        change.accept(form);
        CategoryDefinition category = new CategoryDefinition().withName("GPU");
        Map<String, Object> variables = new HashMap<>();
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("existing", edit);
        variables.put("category", category);
        variables.put("prefillNotice", null);
        variables.put("labels", List.of("RTX 5080", "RTX 5090"));
        variables.put("pricingGroups", List.of("Default", "Ultra Premium"));
        variables.put("availabilityTypes", Arrays.stream(ProductAvailabilityType.values()).map(Enum::name).toList());
        variables.put("storeMarketplaces", List.of(Map.of("name", "allegro", "displayName", "Allegro")));
        variables.put("formAction", "/dashboard/catalogs/c1/category/k1/products/p1");
        variables.put("backHref", "/dashboard/catalogs/c1/category/k1");
        variables.put("pageTitle", "Gigabyte RTX 5080");
        variables.put("leadParts", lead);
        variables.put("deleteHref", edit ? "/dashboard/catalogs/c1/category/k1/products/p1/delete" : null);
        variables.put("productId", edit ? "p1" : null);
        variables.put("openStock", false);
        variables.put("openClient", true);
        variables.put("redirectTo", null);
        Context context = new Context();
        context.setVariables(variables);
        return EnglishFragmentTemplateEngine.create().process("catalog/product", context);
    }

    @Test
    void theHeaderCarriesOneTitleAndTheDeleteLinkOnce() {
        // when
        String html = rendered(true);

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(occurrences(html, "/dashboard/catalogs/c1/category/k1/products/p1/delete")).isEqualTo(1);
        assertThat(html.indexOf("cl-page-actions")).isLessThan(html.indexOf("/products/p1/delete"));
        assertThat(html).doesNotContain("??");
    }

    /** Support conversations quote the product id; a saved product states it instead of hiding it in the address. */
    @Test
    void aSavedProductStatesItsIdAndANewOneHasNone() {
        // when / then
        assertThat(rendered(true)).contains("ID: p1");
        assertThat(rendered(false)).doesNotContain("ID: ");
    }

    @Test
    void aNewProductHasNoDeleteLinkAndOffersTheAddButton() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).doesNotContain("/products/p1/delete").contains("Add the product").doesNotContain("Save the product");
    }

    @Test
    void theSavedValuesAndTheRepeatedRowsComeBackWithTheirFieldIds() {
        // when
        String html = rendered(true);

        // then
        assertThat(html).contains("value=\"Gigabyte RTX 5080\"").contains("value=\"4719331361600\"")
                .contains("name=\"brand\"").contains("value=\"Gigabyte\"");
        assertThat(html).contains("id=\"customAttribute-0-name\"").contains("name=\"customAttributes[0].name\"")
                .contains("id=\"customAttributeFilter-0-name\"").contains("name=\"customAttributesFilters[0].name\"")
                .contains("name=\"customAttributesFilters[0].category\"");
        assertThat(occurrences(html, "type=\"radio\"")).isEqualTo(ProductAvailabilityType.values().length);
        assertThat(html).contains("<option value=\"=\" selected");
    }

    @Test
    void anErrorIsShownAtTheFieldItsKeyNames() {
        // given
        Product product = new Product("k1", "pim-1", "4719331361600", "GV-N5080", "Gigabyte", "RTX 5080", "X", "Default");
        product.setProductId("p1");
        product.getCustomAttributes().add(new ProductCustomAttribute());
        ProductForm form = ProductForm.from(product);
        Map<String, Object> variables = new HashMap<>();
        variables.put("form", form);
        variables.put("errors", Map.of("name", "product.error.name.required",
                ProductForm.fieldId(ProductForm.ATTRIBUTE, 0, "name"), "product.error.attribute.incomplete"));
        variables.put("existing", true);
        variables.put("category", new CategoryDefinition().withName("GPU"));
        variables.put("prefillNotice", "EAN 1 is no longer in the inventory");
        variables.put("labels", List.of());
        variables.put("pricingGroups", List.of("Default"));
        variables.put("availabilityTypes", List.of("BasedOnSupply"));
        variables.put("storeMarketplaces", List.of());
        variables.put("formAction", "/dashboard/catalogs/c1/category/k1/products/p1");
        variables.put("backHref", "/dashboard/catalogs/c1/category/k1");
        variables.put("pageTitle", "X");
        variables.put("leadParts", List.of(new CatalogProductsController.LeadPart("EAN 4719331361600", false)));
        variables.put("deleteHref", null);
        variables.put("openStock", false);
        variables.put("openClient", true);
        variables.put("redirectTo", null);
        Context context = new Context();
        context.setVariables(variables);

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/product", context);

        // then
        assertThat(html).contains("href=\"#name\"").contains("href=\"#customAttribute-0-name\"")
                .contains("id=\"customAttribute-0-name\"").contains("cl-alert is-warn")
                .contains("EAN 1 is no longer in the inventory").doesNotContain("??");
    }

    /** The form of a product still waiting for its PIM entry: saved without one, its codes stay editable. */
    private static void pending(ProductForm form) {
        form.rememberSaved(new Product("k1", null, "4719331361600", "GV-N5080", null, "RTX 5080", "Gigabyte RTX 5080",
                "Default"));
    }

    /** The label of a field, from its opening tag to its end, to see what it says next to the name of the field. */
    private static String labelOf(String html, String field) {
        int start = html.indexOf("for=\"" + field + "\"");
        return html.substring(start, html.indexOf("</label>", start));
    }

    /** A product the PIM knows shows its codes read-only and posts none of them; the page says why. */
    @Test
    void aProductKnownToThePimShowsItsCodesReadOnlyAndPostsNone() {
        // when
        String html = rendered(true);

        // then
        assertThat(html).contains("id=\"eanDisplay\" readonly").contains("id=\"manufacturerCodeDisplay\" readonly")
                .contains("value=\"4719331361600\"").contains("value=\"GV-N5080\"")
                .contains("The product has a PIM entry, so its EAN and manufacturer code cannot be changed.");
        assertThat(html).doesNotContain("name=\"ean\"").doesNotContain("name=\"manufacturerCode\"");
    }

    /** Both codes are required: a pending product and a new one post them, neither field is marked optional. */
    @Test
    void aPendingOrANewProductHasBothCodesRequired() {
        // when
        String pendingPage = rendered(true, ProductTemplateTest::pending);
        String newPage = rendered(false, form -> form.rememberSaved(null));

        // then
        assertThat(List.of(pendingPage, newPage)).allSatisfy(html -> {
            assertThat(html).contains("name=\"ean\"").contains("name=\"manufacturerCode\"")
                    .doesNotContain("eanDisplay").doesNotContain("The product has a PIM entry");
            assertThat(labelOf(html, "ean")).doesNotContain("cl-optional");
            assertThat(labelOf(html, "manufacturerCode")).doesNotContain("cl-optional");
            assertThat(occurrences(html, " required")).isGreaterThanOrEqualTo(3);
        });
    }

    /** The error summary links to "#" + the key of the error, so every key the form can produce must be an id here. */
    @Test
    void everyErrorKeyNamesAnElementOfThePage() {
        // given
        List<String> fields = List.of("name", "ean", "manufacturerCode", "label", "availabilityType", "suggestedRetailPrice",
                "maxRetailPrice", "estimatedDeliveryDays", "pricingGroup", "stockExpectedQty", "restockPricePromo",
                "restockPriceStandard", "marketplaces");
        Map<String, String> errors = new LinkedHashMap<>();
        fields.forEach(field -> errors.put(field, "product.error.amount.invalid"));

        // when -- a pending product: only its codes are editable, so only there can they carry an error
        String html = rendered(true, errors, List.of(new CatalogProductsController.LeadPart("EAN 4719331361600", false)),
                ProductTemplateTest::pending);

        // then
        assertThat(fields).allSatisfy(field -> assertThat(html).contains("id=\"" + field + "\""));
        assertThat(html).doesNotContain("??");
    }

    /**
     * The line under the title is a block of the template: the brand and the codes come from the operator and are
     * shown as text, and "no PIM entry" is a pill of the design system, not markup built in the controller (D-M50).
     */
    @Test
    void theLeadShowsTheIdentifiersAsTextAndAMissingPimEntryAsAPill() {
        // when
        String html = rendered(true, Map.of(), List.of(new CatalogProductsController.LeadPart("EAN 1", false),
                new CatalogProductsController.LeadPart("No PIM entry", true),
                new CatalogProductsController.LeadPart("<b>Brand</b>", false)));

        // then
        int open = html.indexOf("<p class=\"cl-page-lead\">");
        String lead = html.substring(open, html.indexOf("</p>", open));
        assertThat(lead).contains("EAN 1").contains(" · ").contains("<span class=\"cl-status is-warn\">No PIM entry</span>")
                .contains("&lt;b&gt;Brand&lt;/b&gt;").doesNotContain("<b>");
        assertThat(lead.indexOf("EAN 1")).isLessThan(lead.indexOf("No PIM entry"));
        assertThat(lead.indexOf("No PIM entry")).isLessThan(lead.indexOf("Brand"));
    }

    /**
     * RF-20: a product prefilled from the inventory may carry a label the category does not offer. Nothing is saved
     * yet, so there is no label to keep: the select starts from "—" instead of an option the save would refuse.
     */
    @Test
    void aNewProductOffersNoOptionOutsideTheLabelList() {
        // when
        String html = rendered(false, form -> form.setLabel("RTX 4060"));

        // then
        assertThat(html).doesNotContain("RTX 4060 (outside the list)").doesNotContain("value=\"RTX 4060\"");
        assertThat(rendered(true, form -> form.setLabel("RTX 4060")))
                .contains("<option value=\"RTX 4060\" selected>RTX 4060 (outside the list)</option>");
    }

    @Test
    void aFilterWithoutItsCategoryIsMarkedAtTheCategoryField() {
        // when
        String html = rendered(true, Map.of(ProductForm.fieldId(ProductForm.FILTER, 0, "category"),
                "product.error.filter.incomplete"));

        // then
        int id = html.indexOf("id=\"customAttributeFilter-0-category\"");
        String field = html.substring(html.lastIndexOf("<input", id), html.indexOf(">", id));
        assertThat(field).contains("is-invalid").contains("aria-invalid=\"true\"");
        assertThat(occurrences(html, "cl-field-error")).isEqualTo(1);
    }

    /** RF-29: the error of an unfinished filter is shown and marked at the field its key names, here the operator. */
    @Test
    void anUnfinishedFilterIsMarkedAtTheFieldItsErrorNames() {
        // when
        String html = rendered(true, Map.of(ProductForm.fieldId(ProductForm.FILTER, 0, "operator"),
                "product.error.filter.incomplete"));

        // then
        int select = html.indexOf("id=\"customAttributeFilter-0-operator\"");
        String operator = html.substring(html.lastIndexOf("<select", select), html.indexOf(">", select));
        assertThat(operator).contains("is-invalid").contains("aria-invalid=\"true\"");
        int name = html.indexOf("id=\"customAttributeFilter-0-name\"");
        String nameField = html.substring(html.lastIndexOf("<input", name), html.indexOf(">", name));
        assertThat(nameField).doesNotContain("is-invalid");
        assertThat(occurrences(html, "cl-field-error")).isEqualTo(1);
    }

    /**
     * OD-2: the select shows the group the product is priced by. One the category no longer lists is an option of its
     * own, marked as outside the list, instead of the select falling back to the first group; one listed in another
     * case is selected and posted back as saved.
     */
    @Test
    void theSavedPricingGroupIsSelectedEvenOutsideTheListOrInAnotherCase() {
        // when
        String outside = rendered(true, form -> form.setPricingGroup("Ultra"));
        String otherCase = rendered(true, form -> form.setPricingGroup("ultra premium"));
        String created = rendered(false, form -> form.setPricingGroup("Ultra"));

        // then
        assertThat(outside).contains("<option value=\"Ultra\" selected>Ultra (outside the list)</option>");
        assertThat(otherCase).contains("<option value=\"ultra premium\" selected=\"selected\">Ultra Premium</option>")
                .doesNotContain("(outside the list)");
        assertThat(created).doesNotContain("Ultra (outside the list)");
    }

    /** OD-6: approvals for marketplaces the store no longer has come back hidden, so a save does not drop them. */
    @Test
    void approvalsForUnconnectedMarketplacesTravelHidden() {
        // when
        String html = rendered(true, form -> form.setMarketplaces(List.of("allegro", "Morele")));

        // then
        assertThat(html).contains("<input type=\"hidden\" name=\"marketplaces\" value=\"Morele\">")
                .doesNotContain("<input type=\"hidden\" name=\"marketplaces\" value=\"allegro\">");
    }

    /**
     * D-I9 / RF-31: every list of the client section is a group under its own heading and a sentence on what it holds,
     * like the quick filters; before, the attributes, the custom filters and the metadata were only an "Add" button.
     */
    @Test
    void theThreeClientListsCarryAHeadingAndAHelp() {
        // when
        String html = rendered(true);

        // then
        for (String title : List.of("Attributes", "Custom filters", "Metadata")) {
            String legend = "<legend class=\"cl-fieldset-title\">" + title + "</legend>";
            assertThat(html).contains(legend);
            String after = html.substring(html.indexOf(legend) + legend.length()).stripLeading();
            assertThat(after).startsWith("<p class=\"cl-help\">");
        }
        assertThat(html.indexOf(">Attributes</legend>")).isLessThan(html.indexOf("id=\"customAttribute-0-name\""));
        assertThat(html.indexOf(">Custom filters</legend>")).isLessThan(html.indexOf("id=\"customAttributeFilter-0-name\""));
        assertThat(html.indexOf(">Custom filters</legend>")).isGreaterThan(html.indexOf("id=\"customAttribute-0-name\""));
        assertThat(html).doesNotContain("??");
    }

    /** D-M30: the suppliers of the maximum price show an example of what goes in, as the mock-up does. */
    @Test
    void theSuppliersOfTheMaximumPriceShowAnExample() {
        // when
        String html = rendered(true);

        // then
        int field = html.indexOf("id=\"maxRetailPriceSuppliers\"");
        String input = html.substring(html.lastIndexOf("<input", field), html.indexOf(">", field));
        assertThat(input).contains("placeholder=\"e.g. Acme, Elko\"").contains("name=\"maxRetailPriceSuppliers\"")
                .contains("aria-describedby=\"maxRetailPriceSuppliers-help\"");
        assertThat(html).contains("<label class=\"cl-label\" for=\"maxRetailPriceSuppliers\">");
    }
}
