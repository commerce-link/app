package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoriesTest {

    @Test
    void parsesKnownCategoryKey() {
        // when / then
        assertThat(ProductCategories.tryParse("CPU")).contains("CPU");
    }

    @Test
    void returnsEmptyForUnknownCategoryKey() {
        // when / then
        assertThat(ProductCategories.tryParse("Smartwatches")).isEmpty();
    }

    @Test
    void returnsEmptyForNullCategoryKey() {
        // when / then
        assertThat(ProductCategories.tryParse(null)).isEmpty();
    }

    @Test
    void knownCategoriesMatchTheInventoryDictionarySnapshot() {
        // when / then
        assertThat(ProductCategories.KNOWN).containsExactlyInAnyOrder(
                "CPU",
                "Cooler",
                "GPU",
                "Motherboard",
                "PSU",
                "Storage",
                "Memory",
                "Case",
                "Fan",
                "ModdingPC",
                "Other",
                "Services",
                "Laptops",
                "Desktops",
                "Workstations",
                "Servers",
                "AllInOnePCs",
                "GraphicsTablets",
                "Software",
                "Smartphones",
                "StationaryPhones",
                "Tablets",
                "SmartphoneCases",
                "ScreenProtectors",
                "Chargers",
                "Powerbanks",
                "MobileHeadphones",
                "Printers",
                "LaserPrinters",
                "InkPrinters",
                "PhotoPrinters",
                "LargeFormatPrinters",
                "LabelPrinters",
                "Printers3D",
                "Scanners",
                "MultifunctionPrinters",
                "Displays",
                "Keyboards",
                "Mice",
                "KeyboardsAndMice",
                "Headphones",
                "Microphones",
                "Webcams",
                "Speakers",
                "MousePads",
                "GamingChairs",
                "OfficeChairs",
                "GamingDesks",
                "OfficeDesks",
                "StandingDesks",
                "MonitorMounts",
                "Footrests");
    }
}
