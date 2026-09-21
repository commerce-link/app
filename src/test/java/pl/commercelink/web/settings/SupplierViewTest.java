package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.web.dtos.SupplierAdminSettingsForm;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierViewTest {

    private static final String PATH = "/dashboard/store/suppliers";

    private static SupplierConnectionView connection(String identity, ConnectionMode mode, boolean pricing, boolean fulfilment,
                                                     boolean enabled, boolean known) {
        return new SupplierConnectionView(identity, mode == ConnectionMode.MANUAL ? null : "Acme", identity, mode, pricing,
                fulfilment, enabled, null, null, null, null, known);
    }

    @Test
    void aSupplierWhoseIntegrationIsGoneCanOnlyBeDisconnected() {
        // when
        SupplierView view = SupplierView.of(connection("Gone", ConnectionMode.OWN, true, true, true, false), true, PATH);

        // then
        assertThat(view.state()).isEqualTo(SupplierView.State.UNAVAILABLE);
        assertThat(view.editHref()).isNull();
        assertThat(view.removeHref()).isEqualTo(PATH + "/Gone/disconnect");
    }

    @Test
    void aPriceListIsDeletedAndHasNoSchedule() {
        // when
        SupplierView view = SupplierView.of(connection("manual-abcd1234", ConnectionMode.MANUAL, true, true, false, true), false, PATH);

        // then
        assertThat(view.state()).isEqualTo(SupplierView.State.DISABLED);
        assertThat(view.source()).isEqualTo(SupplierView.Source.CSV);
        assertThat(view.schedule()).isNull();
        assertThat(view.removeHref()).isEqualTo(PATH + "/manual-abcd1234/delete");
    }

    @Test
    void onlyAnOwnConnectionShowsItsSchedule() {
        // when / then
        assertThat(SupplierView.of(connection("Acme-abcd1234", ConnectionMode.OWN, true, true, true, true), false, PATH).schedule())
                .isNotNull();
        assertThat(SupplierView.of(connection("Acme", ConnectionMode.GLOBAL, true, true, true, true), false, PATH).schedule())
                .isNull();
    }

    @Test
    void whatTheStoreUsesTheSupplierForIsOneSentence() {
        // when / then
        assertThat(SupplierView.of(connection("A", ConnectionMode.GLOBAL, true, false, true, true), false, PATH).usageKey())
                .isEqualTo("store.suppliers.usage.pricing");
        assertThat(SupplierView.of(connection("A", ConnectionMode.GLOBAL, false, true, true, true), false, PATH).usageKey())
                .isEqualTo("store.suppliers.usage.fulfilment");
        SupplierView unused = SupplierView.of(connection("A", ConnectionMode.GLOBAL, false, false, true, true), false, PATH);
        assertThat(unused.usageKey()).isEqualTo("store.suppliers.usage.none");
        assertThat(unused.unused()).isTrue();
    }

    @Test
    void theCacheTimeIsEmptyOrMinutesFromOneToADay() {
        // given
        SupplierAdminSettingsForm form = new SupplierAdminSettingsForm();

        // when / then
        form.setInventoryCacheTtlMinutes("");
        assertThat(form.validate()).isEmpty();
        assertThat(form.cacheTtlMinutes()).isNull();
        form.setInventoryCacheTtlMinutes("1440");
        assertThat(form.validate()).isEmpty();
        assertThat(form.cacheTtlMinutes()).isEqualTo(1440);
        form.setInventoryCacheTtlMinutes("0");
        assertThat(form.validate()).containsOnlyKeys("inventoryCacheTtlMinutes");
        form.setInventoryCacheTtlMinutes("1441");
        assertThat(form.validate()).containsOnlyKeys("inventoryCacheTtlMinutes");
    }
}
