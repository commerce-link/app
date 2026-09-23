package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FulfilmentSettingsFormTest {

    private static FulfilmentSettingsForm valid() {
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.setOrderAssemblyDays("2");
        form.setOrderRealizationDays("0");
        form.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment.name());
        for (ShipmentType type : ShipmentType.values()) {
            form.getPreferredShippingDays().put(type.name(), new ArrayList<>(List.of("MONDAY")));
        }
        return form;
    }

    @Test
    void fromListsTheWeekdaysOfEveryShipmentTypeForAStoreWithoutConfiguredDays() {
        // given
        Store store = new Store();

        // when
        FulfilmentSettingsForm form = FulfilmentSettingsForm.from(store);

        // then
        for (ShipmentType type : ShipmentType.values()) {
            assertThat(form.getPreferredShippingDays().get(type.name()))
                    .containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
        }
        assertThat(form.hasPreferredShippingDay("Courier", "MONDAY")).isTrue();
        assertThat(form.hasPreferredShippingDay("Courier", "SATURDAY")).isFalse();
    }

    @Test
    void fromReadsTheConfiguredDaysOfAShipmentType() {
        // given
        Store store = new Store();
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setPreferredShippingDays(Map.of("PersonalCollection", List.of("SATURDAY", "MONDAY")));
        store.setFulfilmentConfiguration(config);

        // when
        FulfilmentSettingsForm form = FulfilmentSettingsForm.from(store);

        // then
        assertThat(form.getPreferredShippingDays().get("PersonalCollection")).containsExactly("MONDAY", "SATURDAY");
        assertThat(form.getPreferredShippingDays().get("Courier")).hasSize(5);
    }

    @Test
    void aShipmentTypeWithoutAnyDayIsRejected() {
        // given
        FulfilmentSettingsForm form = valid();
        form.getPreferredShippingDays().remove("PickupPoint");

        // when / then
        assertThat(form.validate()).containsOnlyKeys("preferredShippingDays");
    }

    @Test
    void aShipmentTypeWithOnlyUnknownDaysIsRejected() {
        // given
        FulfilmentSettingsForm form = valid();
        form.getPreferredShippingDays().put("Courier", List.of("FUNDAY"));

        // when / then
        assertThat(form.validate()).containsOnlyKeys("preferredShippingDays");
    }

    @Test
    void theSavedConfigurationKeepsTheDaysInWeekOrderAndDropsUnknownNames() {
        // given
        FulfilmentSettingsForm form = valid();
        form.getPreferredShippingDays().put("PersonalCollection", List.of("SATURDAY", "FUNDAY", "MONDAY"));

        // when
        FulfilmentConfiguration saved = form.toFulfilmentConfiguration(new Store());

        // then
        assertThat(saved.getPreferredShippingDays().get("PersonalCollection")).containsExactly("MONDAY", "SATURDAY");
        assertThat(saved.getPreferredShippingDays().get("Courier")).containsExactly("MONDAY");
        assertThat(saved.preferredShippingDaysFor(ShipmentType.PersonalCollection))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
    }

    @Test
    void fromReadsTheFulfilmentFieldsOfTheStore() {
        // given
        Store store = new Store();
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setOrderAssemblyDays(2);
        config.setOrderRealizationDays(4);
        config.setAutomatedFulfilment(true);
        config.setDefaultFulfilmentType(FulfilmentType.DirectToConsumer);
        config.setClientOrderPageEnabled(true);
        config.setClientShippingAddressChangeEnabled(true);
        store.setFulfilmentConfiguration(config);

        // when
        FulfilmentSettingsForm form = FulfilmentSettingsForm.from(store);

        // then
        assertThat(form.getOrderAssemblyDays()).isEqualTo("2");
        assertThat(form.getOrderRealizationDays()).isEqualTo("4");
        assertThat(form.isAutomatedFulfilment()).isTrue();
        assertThat(form.getDefaultFulfilmentType()).isEqualTo("DirectToConsumer");
        assertThat(form.isClientOrderPageEnabled()).isTrue();
        assertThat(form.isClientShippingAddressChangeEnabled()).isTrue();
    }

    @Test
    void fromDefaultsSafelyForAStoreWithoutAFulfilmentConfigurationYet() {
        // given
        Store store = new Store();

        // when
        FulfilmentSettingsForm form = FulfilmentSettingsForm.from(store);

        // then
        assertThat(form.getOrderAssemblyDays()).isEqualTo("0");
        assertThat(form.getDefaultFulfilmentType()).isEqualTo("WarehouseFulfilment");
        assertThat(form.isAutomatedFulfilment()).isFalse();
    }

    @Test
    void aValidFormHasNoErrors() {
        // when / then
        assertThat(valid().validate()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "-1", "2.5", "abc", "61", "1000"})
    void dayCountsOutsideZeroToSixtyAreRejected(String days) {
        // given
        FulfilmentSettingsForm form = valid();
        form.setOrderAssemblyDays(days);

        // when / then
        assertThat(form.validate()).containsOnlyKeys("orderAssemblyDays");
    }

    @Test
    void anUnknownFulfilmentTypeIsRejected() {
        // given
        FulfilmentSettingsForm form = valid();
        form.setDefaultFulfilmentType("Teleport");

        // when / then
        assertThat(form.validate()).containsOnlyKeys("defaultFulfilmentType");
    }

    @Test
    void theSavedConfigurationKeepsTheSupplierSettingsOfTheStore() {
        // given
        Store store = new Store();
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(true);
        config.setInventoryCacheTtlMinutes(15);
        store.setFulfilmentConfiguration(config);
        FulfilmentSettingsForm form = valid();
        form.setOrderAssemblyDays(" 3 ");

        // when
        FulfilmentConfiguration saved = form.toFulfilmentConfiguration(store);

        // then
        assertThat(saved).isNotSameAs(config);
        assertThat(saved.getOrderAssemblyDays()).isEqualTo(3);
        assertThat(saved.isCanUseGlobalSuppliers()).isTrue();
        assertThat(saved.getInventoryCacheTtlMinutes()).isEqualTo(15);
    }
}
