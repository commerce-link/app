package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveriesControllerProviderOptionsTest {

    @Test
    void providerFilterOptionsIncludeTheInternalWarehouse() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("AcmeB-k7f3a9c2", ConnectionMode.OWN, true, true);
        connection.setLabel("Hurtownia");
        connection.setEnabled(true);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connection)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        SupplierLabelMap labels = new SupplierLabels(null).forStore(store);

        // when
        List<SupplierLabelMap.Option> options = DeliveriesController.providerFilterOptions(labels);

        // then
        assertThat(options).extracting(SupplierLabelMap.Option::identity)
                .containsExactly("AcmeB-k7f3a9c2", SupplierRegistry.WAREHOUSE);
    }

    @Test
    void providerFilterUsesTheTypedNameWhenOtherSupplierIsChosen() {
        // when / then
        assertThat(DeliveriesController.providerFilter(SupplierChoice.CUSTOM, "  HURT-ABC ")).isEqualTo("HURT-ABC");
        assertThat(DeliveriesController.providerFilter(SupplierChoice.CUSTOM, "   ")).isNull();
        assertThat(DeliveriesController.providerFilter("AcmeB-k7f3a9c2", "ignored")).isEqualTo("AcmeB-k7f3a9c2");
    }
}
