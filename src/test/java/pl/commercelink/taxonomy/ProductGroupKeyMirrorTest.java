package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductGroupKeyMirrorTest {

    @Test
    void itemTypeOfGroupKeyMirrorsOfEnumForEveryGroup() {
        // given / then
        for (ProductGroup group : ProductGroup.values()) {
            assertThat(ItemType.of(group.name())).isEqualTo(ItemType.of(group));
        }
        assertThat(ItemType.of("Services")).isEqualTo(ItemType.SERVICE);
        assertThat(ItemType.of("PcComponents")).isEqualTo(ItemType.PRODUCT);
    }

    @Test
    void enabledProductGroupKeysMirrorEnabledGroupsInOrder() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(List.of(ProductGroup.Peripherals, ProductGroup.Furniture));
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when / then
        assertThat(configuration.getEnabledProductGroupKeys()).containsExactly("Peripherals", "Furniture");
        assertThat(store.getEnabledProductGroupKeys()).containsExactly("Peripherals", "Furniture");
    }

    @Test
    void enabledProductGroupKeysIsEmptyWhenNoGroupsOrConfig() {
        // given
        Store storeWithoutConfig = new Store();
        FulfilmentConfiguration emptyConfig = new FulfilmentConfiguration();
        emptyConfig.setEnabledProductGroups(List.of());
        Store storeWithEmptyConfig = new Store();
        storeWithEmptyConfig.setFulfilmentConfiguration(emptyConfig);

        // when / then
        assertThat(storeWithoutConfig.getEnabledProductGroupKeys()).isEmpty();
        assertThat(storeWithEmptyConfig.getEnabledProductGroupKeys()).isEmpty();
    }
}
