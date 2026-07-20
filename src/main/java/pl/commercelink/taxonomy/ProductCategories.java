package pl.commercelink.taxonomy;

import java.util.Optional;
import java.util.Set;

public final class ProductCategories {

    static final Set<String> KNOWN = Set.of(
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

    private ProductCategories() {
    }

    public static Optional<String> tryParse(String categoryKey) {
        return categoryKey != null && KNOWN.contains(categoryKey)
                ? Optional.of(categoryKey)
                : Optional.empty();
    }
}
