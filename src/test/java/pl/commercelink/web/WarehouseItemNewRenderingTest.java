package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.warehouse.builtin.WarehouseItemAddForm;

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
    void errorsAreListedAndMarkedOnTheirFields() {
        // when
        String html = render(form(), Map.of("cost", "warehouse.item.new.error.cost"), false, "Service said no");

        // then
        assertThat(html).contains("warehouse-item-errors").contains("is-invalid").contains("Service said no")
                .contains("Enter the purchase price");
    }
}
