package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.warehouse.builtin.WarehouseItemAddForm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseItemNewRenderingTest {

    private static String render(WarehouseItemAddForm form, Map<String, String> errors, boolean mfnUnknown, String errorMessage) {
        Context context = new Context();
        context.setVariable("form", form);
        context.setVariable("errors", errors);
        context.setVariable("errorArgs", Map.of());
        context.setVariable("mfnUnknown", mfnUnknown);
        context.setVariable("errorMessage", errorMessage);
        context.setVariable("vatRate", Price.DEFAULT_VAT_RATE);
        context.setVariable("statuses", List.of(FulfilmentStatus.New, FulfilmentStatus.Allocation));
        context.setVariable("suppliers", List.of(new SupplierLabelMap.Option("Acme", "Acme Ltd")));
        context.setVariable("categoryGroups", List.of(new StoreCategories.Group("Computers", List.of("SSD drives"))));
        return EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{warehouse-item-new :: form}\"></div>", context);
    }

    private static WarehouseItemAddForm form() {
        WarehouseItemAddForm form = new WarehouseItemAddForm();
        form.setManufacturerCode("XPG-S70-2TB");
        form.setCost("689,00");
        form.setQty("2");
        return form;
    }

    @Test
    void aKnownCodeShowsNoProductDataGroup() {
        // when
        String html = render(form(), Map.of(), false, null);

        // then
        assertThat(html).doesNotContain("id=\"product-data\"").doesNotContain("??");
        assertThat(html).contains("id=\"warehouseItemNewForm\"").contains("data-cl-submit-once");
        assertThat(html).contains("value=\"XPG-S70-2TB\"").contains("value=\"689,00\"");
    }

    @Test
    void anUnknownCodeRevealsProductDataAndWarnsWithTheCode() {
        // when
        String html = render(form(), Map.of(), true, null);

        // then
        assertThat(html).contains("id=\"product-data\"").contains("name=\"ean\"").contains("name=\"category\"");
        assertThat(html).contains("We did not find code XPG-S70-2TB").contains("SSD drives");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void theSupplierPickerOffersTheStoresSuppliersAndTheCustomOption() {
        // when
        String html = render(form(), Map.of(), false, null);

        // then
        assertThat(html).contains("name=\"supplier\"").contains("value=\"Acme\"").contains("Acme Ltd")
                .contains("value=\"__custom__\"").contains("name=\"customSupplier\"");
    }

    @Test
    void theCostFieldCarriesTheNetGrossSwitchAndItsHint() {
        // when
        String html = render(form(), Map.of(), false, null);

        // then
        assertThat(html).contains("data-cl-cost-field").contains("name=\"priceType\"").contains("data-cl-cost-hint")
                .contains("data-tax=\"1.23\"").contains("data-value=\"gross\"");
    }

    @Test
    void theStatusChoicesUseTheWarehouseStatusNamesOfTheList() {
        // when
        String html = render(form(), Map.of(), false, null);

        // then
        assertThat(html).contains("<span class=\"cl-choice-title\">In allocation</span>")
                .doesNotContain("<span class=\"cl-choice-title\">Allocating</span>");
    }

    @Test
    void errorsAreListedAndMarkedOnTheirFields() {
        // when
        String html = render(form(), Map.of("cost", "warehouse.item.new.error.cost"), false, "Service said no");

        // then
        assertThat(html).contains("warehouse-item-errors").contains("is-invalid").contains("Service said no")
                .contains("Enter the purchase price");
    }

    private static String source(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + template));
    }

    @Test
    void theTextFieldsComeFromTheSharedFragmentWithTheirInputModesAndExample() throws Exception {
        // given
        String template = source("warehouse-item-new.html");

        // when
        String html = render(form(), Map.of(), true, null);

        // then
        assertThat(template).contains("settings-form :: textFieldWith('manufacturerCode'")
                .contains("settings-form :: textFieldWith('qty'").contains("settings-form :: textFieldWith('name'")
                .contains("settings-form :: textFieldWith('ean'");
        assertThat(html).contains("id=\"manufacturerCode\"").contains("placeholder=\"")
                .containsPattern("id=\"qty\"[^>]*inputmode=\"numeric\"").containsPattern("id=\"ean\"[^>]*inputmode=\"numeric\"");
    }

    @Test
    void theFormIsValidatedByTheServerNotByTheBrowser() {
        // when
        String html = render(form(), Map.of(), true, null);

        // then
        assertThat(html).contains("novalidate")
                .containsPattern("<input type=\"hidden\" name=\"productDataShown\" value=\"true\"");
    }

    @Test
    void theNameTakesTheFocusOnlyWhenThereIsNoErrorSummaryToFocus() {
        // when
        String withoutErrors = render(form(), Map.of(), true, null);
        String withErrors = render(form(), Map.of("name", "warehouse.item.new.error.name"), true, null);

        // then
        assertThat(withoutErrors).containsPattern("id=\"name\"[^>]*autofocus");
        assertThat(withErrors).doesNotContain("autofocus").contains("data-cl-error-summary")
                .contains("href=\"#name\"").contains("Enter the product name.");
    }

    @Test
    void anEmptySupplierChoiceIsAnErrorOfTheSelectAndTheSummaryLinksToIt() {
        // when
        String html = render(form(), Map.of("new-supplier", "order.item.assign.supplier.required"), false, null);

        // then
        assertThat(html).contains("href=\"#new-supplier\"").contains("Choose a supplier.")
                .containsPattern("<select[^>]*id=\"new-supplier\"[^>]*aria-invalid=\"true\"")
                .containsPattern("<select[^>]*id=\"new-supplier\"[^>]*aria-describedby=\"new-supplier-error\"")
                .contains("id=\"new-supplier-error\"");
    }

    @Test
    void aRefusedTypedSupplierNameIsAnErrorOfTheTypedField() {
        // given
        WarehouseItemAddForm form = form();
        form.setSupplier("__custom__");
        form.setCustomSupplier("");

        // when
        String html = render(form, Map.of("new-supplier-custom", "order.item.assign.supplier.custom.required"), false, null);

        // then
        assertThat(html).contains("href=\"#new-supplier-custom\"").contains("Enter the supplier name.")
                .containsPattern("id=\"new-supplier-custom\"[^>]*aria-invalid=\"true\"")
                .doesNotContainPattern("<select[^>]*aria-invalid");
    }

    @Test
    void thePriceErrorSaysZeroOrMore() {
        // when
        String html = render(form(), Map.of("cost", "warehouse.item.new.error.cost"), false, null);

        // then
        assertThat(html).contains("Enter the purchase price: 0 or more.");
    }

    @Test
    void aServiceRefusalTakesTheFocusLikeAnErrorSummary() {
        // when
        String html = render(form(), Map.of(), false, "Service said no");

        // then
        assertThat(html).containsPattern("tabindex=\"-1\"[^>]*data-cl-error-summary[^>]*>\\s*<i[^>]*></i>\\s*<p class=\"cl-alert-text\">Service said no");
    }

    @Test
    void thePageIsANarrowFormWithAHeaderElementAndFocusesItsErrors() throws Exception {
        // when
        String template = source("warehouse-item-new.html");
        String script = Files.readString(Path.of("src/main/resources/static/js/error-summary.js"));

        // then
        assertThat(script).contains("querySelector('[data-cl-error-summary]')").contains("summary.focus()");
        assertThat(template).contains("cl-page-body is-form").doesNotContain("is-wide")
                .contains("<header class=\"cl-page-header\">").contains("/js/error-summary.js");
    }
}
