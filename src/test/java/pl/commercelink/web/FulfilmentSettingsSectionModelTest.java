package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

import static org.assertj.core.api.Assertions.assertThat;

class FulfilmentSettingsSectionModelTest {

    private static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setOrderAssemblyDays(3);
        config.setOrderRealizationDays(7);
        config.setAutomatedFulfilment(true);
        config.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);
        config.setCanUseGlobalSuppliers(true);
        config.setInventoryCacheTtlMinutes(15);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void renderSectionReturnsTheNoArgumentFragmentWithEveryFieldPublishedOnTheModel() {
        // given
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = FulfilmentSettingsSectionModel.renderSection(store(), true, "Saved.", true, model);

        // then -- a no-argument view name: ThymeleafView rejects positional fragment parameters in
        // a view specification, so a regression back to the parameterized selector (with or
        // without a stray "(") is caught here rather than only at runtime
        assertThat(view).isEqualTo("fragments/fulfilment-settings-section :: section");
        assertThat(view).doesNotContain("(");

        FulfilmentSettingsForm settings = (FulfilmentSettingsForm) model.getAttribute("sectionSettings");
        assertThat(settings).isNotNull();
        assertThat(settings.getOrderAssemblyDays()).isEqualTo(3);
        assertThat(settings.getOrderRealizationDays()).isEqualTo(7);
        assertThat(settings.isAutomatedFulfilment()).isTrue();
        assertThat(settings.getDefaultFulfilmentType()).isEqualTo(FulfilmentType.WarehouseFulfilment);
        assertThat(settings.isCanUseGlobalSuppliers()).isTrue();
        assertThat(settings.getInventoryCacheTtlMinutes()).isEqualTo(15);

        assertThat(model.getAttribute("sectionIsSuperAdmin")).isEqualTo(true);
        assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("Saved.");
        assertThat(model.getAttribute("sectionRefreshExternalSuppliers")).isEqualTo(true);
    }

    @Test
    void publishesFalseForBothFlagsWhenNeitherApplies() {
        // given
        ConcurrentModel model = new ConcurrentModel();

        // when
        FulfilmentSettingsSectionModel.renderSection(store(), false, null, false, model);

        // then
        assertThat(model.getAttribute("sectionIsSuperAdmin")).isEqualTo(false);
        assertThat(model.getAttribute("sectionSuccessMessage")).isNull();
        assertThat(model.getAttribute("sectionRefreshExternalSuppliers")).isEqualTo(false);
    }
}
