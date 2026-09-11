package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;

class FulfilmentSettingsFormTest {

    @Test
    void fromReadsEveryFieldOffTheStoresFulfilmentConfiguration() {
        // given
        Store store = new Store();
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setOrderAssemblyDays(2);
        config.setOrderRealizationDays(4);
        config.setAutomatedFulfilment(true);
        config.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);
        config.setCanUseGlobalSuppliers(true);
        config.setInventoryCacheTtlMinutes(20);
        store.setFulfilmentConfiguration(config);

        // when
        FulfilmentSettingsForm form = FulfilmentSettingsForm.from(store);

        // then
        assertThat(form.getOrderAssemblyDays()).isEqualTo(2);
        assertThat(form.getOrderRealizationDays()).isEqualTo(4);
        assertThat(form.isAutomatedFulfilment()).isTrue();
        assertThat(form.getDefaultFulfilmentType()).isEqualTo(FulfilmentType.WarehouseFulfilment);
        assertThat(form.isCanUseGlobalSuppliers()).isTrue();
        assertThat(form.getInventoryCacheTtlMinutes()).isEqualTo(20);
    }

    @Test
    void fromDefaultsSafelyForAStoreWithoutAFulfilmentConfigurationYet() {
        // given
        Store store = new Store();
        store.setFulfilmentConfiguration(null);

        // when
        FulfilmentSettingsForm form = FulfilmentSettingsForm.from(store);

        // then
        assertThat(form.getOrderAssemblyDays()).isEqualTo(0);
        assertThat(form.getOrderRealizationDays()).isEqualTo(0);
        assertThat(form.isAutomatedFulfilment()).isFalse();
        assertThat(form.isCanUseGlobalSuppliers()).isFalse();
        assertThat(form.getInventoryCacheTtlMinutes()).isNull();
    }

    @Test
    void toFulfilmentConfigurationCarriesOnlyTheFieldsThisFormEdits() {
        // given
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.setOrderAssemblyDays(1);
        form.setOrderRealizationDays(3);
        form.setAutomatedFulfilment(true);
        form.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);
        form.setCanUseGlobalSuppliers(true);
        form.setInventoryCacheTtlMinutes(10);

        // when
        FulfilmentConfiguration config = form.toFulfilmentConfiguration();

        // then
        assertThat(config.getOrderAssemblyDays()).isEqualTo(1);
        assertThat(config.getOrderRealizationDays()).isEqualTo(3);
        assertThat(config.isAutomatedFulfilment()).isTrue();
        assertThat(config.getDefaultFulfilmentType()).isEqualTo(FulfilmentType.WarehouseFulfilment);
        assertThat(config.isCanUseGlobalSuppliers()).isTrue();
        assertThat(config.getInventoryCacheTtlMinutes()).isEqualTo(10);
        // supplier connections/enabled product groups/categories are not this form's concern --
        // applyStoreSettings fills those in from the existing configuration
        assertThat(config.getSupplierConnections()).isEmpty();
    }
}
