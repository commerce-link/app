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
    void translatesIcecatLeafNamesToInventoryCategoriesThatHadNoLeafBefore() {
        // when / then
        assertThat(InventoryCategoryBridge.toInventoryCategory("Serwery")).isEqualTo("Servers");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Ładowarki do urządzeń przenośnych")).isEqualTo("Chargers");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Uchwyty i stojaki do monitorów")).isEqualTo("MonitorMounts");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Drukarki etykiet")).isEqualTo("LabelPrinters");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Podnóżki")).isEqualTo("Footrests");
    }

    @Test
    void translatesIcecatLeafNamesForMobileAccessoriesAndGamingFurniture() {
        // when / then
        assertThat(InventoryCategoryBridge.toInventoryCategory("Banki mocy")).isEqualTo("Powerbanks");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Pokrowce na telefony komórkowe")).isEqualTo("SmartphoneCases");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Ochraniacze na ekran i tył telefonu")).isEqualTo("ScreenProtectors");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Fotele do gier")).isEqualTo("GamingChairs");
        assertThat(InventoryCategoryBridge.toInventoryCategory("Biurka komputerowe")).isEqualTo("GamingDesks");
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
