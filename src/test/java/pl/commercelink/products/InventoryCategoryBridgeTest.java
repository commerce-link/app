package pl.commercelink.products;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryCategoryBridgeTest {

    @Test
    void translatesIcecatLeafNameToInventoryCategory() {
        // when / then
        assertThat(InventoryCategoryBridge.toInventoryCategory("Karty graficzne")).isEqualTo("GPU");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Głośniki")).isEqualTo("Speakers");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Przetworniki głośnikowe")).isEqualTo("Speakers");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Notebooki/laptopy")).isEqualTo("Laptops");
    }

    @Test
    void passesThroughLegacyEnumValues() {
        // when / then
        assertThat(InventoryCategoryBridge.toInventoryCategory("Case")).isEqualTo("Case");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Services")).isEqualTo("Services");
    }

    @Test
    void passesThroughUnmappedLeafNames() {
        // when / then
        assertThat(InventoryCategoryBridge.toInventoryCategory("Kołdry")).isEqualTo("Kołdry");
    }

    @Test
    void passesThroughNull() {
        // when / then
        assertThat(InventoryCategoryBridge.toInventoryCategory(null)).isNull();
    }
}
