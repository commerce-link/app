package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders fragments/fulfilment-settings-section.html through a real Thymeleaf engine (same
 * technique as SupplierSectionRenderingTest) to prove the fragment's own markup compiles and
 * behaves as expected for the exact argument shapes FulfilmentSettingsSectionModel populates on
 * the model, and that the no-argument wrapper forwards to it correctly.
 */
class FulfilmentSettingsSectionRenderingTest {

    private static final String SECTION =
            "<div th:replace=\"~{fragments/fulfilment-settings-section :: fulfilmentSettingsSection(%s)}\"></div>";

    private TemplateEngine templateEngine() {
        return EnglishFragmentTemplateEngine.create();
    }

    private FulfilmentSettingsForm settings() {
        FulfilmentSettingsForm settings = new FulfilmentSettingsForm();
        settings.setOrderAssemblyDays(2);
        settings.setOrderRealizationDays(5);
        settings.setAutomatedFulfilment(true);
        settings.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);
        settings.setCanUseGlobalSuppliers(true);
        settings.setInventoryCacheTtlMinutes(30);
        return settings;
    }

    @Test
    void rendersEveryFieldAndTheSuperAdminOnlyRowsForASuperAdmin() {
        // given
        Context context = new Context();
        context.setVariable("settings", settings());
        context.setVariable("isSuperAdmin", true);
        context.setVariable("successMessage", "Settings saved.");
        context.setVariable("refreshExternalSuppliers", true);

        // when
        String html = templateEngine().process(
                SECTION.formatted("${settings}, ${isSuperAdmin}, ${successMessage}, ${refreshExternalSuppliers}"),
                context);

        // then
        assertThat(html).doesNotContain("??store");
        assertThat(html).contains("data-success-message=\"Settings saved.\"");
        assertThat(html).contains("data-refresh-external-suppliers=\"true\"");
        assertThat(html).contains("data-order-assembly-days=\"2\"");
        assertThat(html).contains("data-order-realization-days=\"5\"");
        assertThat(html).contains("data-automated-fulfilment=\"true\"");
        assertThat(html).contains("data-can-use-global-suppliers=\"true\"");
        assertThat(html).contains("data-inventory-cache-ttl-minutes=\"30\"");
        assertThat(html).contains("Use Global Supplier Settings"); // store.use.global.suppliers
        assertThat(html).contains("Inventory cache TTL (min)"); // store.inventory.cache.ttl
    }

    @Test
    void hidesTheSuperAdminOnlyRowsForAnOrdinaryAdmin() {
        // given
        Context context = new Context();
        context.setVariable("settings", settings());
        context.setVariable("isSuperAdmin", false);
        context.setVariable("successMessage", null);
        context.setVariable("refreshExternalSuppliers", false);

        // when
        String html = templateEngine().process(
                SECTION.formatted("${settings}, ${isSuperAdmin}, ${successMessage}, ${refreshExternalSuppliers}"),
                context);

        // then -- a non-super-admin must never even receive these two fields in the markup: neither
        // the table row (already pinned above) nor the data attribute a script could still read
        // them from
        assertThat(html).doesNotContain("Use Global Supplier Settings");
        assertThat(html).doesNotContain("Inventory cache TTL (min)");
        assertThat(html).doesNotContain("data-can-use-global-suppliers");
        assertThat(html).doesNotContain("data-inventory-cache-ttl-minutes");
        assertThat(html).doesNotContain("data-success-message");
        assertThat(html).contains("data-refresh-external-suppliers=\"false\"");
    }

    @Test
    void theWrapperFragmentRendersTheSameMarkupAsTheParameterizedFragment() {
        // given -- exactly the model attributes FulfilmentSettingsSectionModel.renderSection sets
        Context context = new Context();
        context.setVariable("sectionSettings", settings());
        context.setVariable("sectionIsSuperAdmin", true);
        context.setVariable("sectionSuccessMessage", "Settings saved.");
        context.setVariable("sectionRefreshExternalSuppliers", true);

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/fulfilment-settings-section :: section}\"></div>", context);

        // then
        assertThat(html).doesNotContain("??store");
        assertThat(html).contains("data-success-message=\"Settings saved.\"");
        assertThat(html).contains("data-refresh-external-suppliers=\"true\"");
        assertThat(html).contains("data-order-assembly-days=\"2\"");
    }
}
