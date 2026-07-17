package pl.commercelink.taxonomy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductCategories {

    private static final Map<String, String> GROUP_BY_CATEGORY = new LinkedHashMap<>();

    static {
        GROUP_BY_CATEGORY.put("CPU", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Cooler", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("GPU", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Motherboard", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("PSU", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Storage", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Memory", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Case", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Fan", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("ModdingPC", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Other", ProductGroups.PC_COMPONENTS);
        GROUP_BY_CATEGORY.put("Services", ProductGroups.SERVICES);
        GROUP_BY_CATEGORY.put("Laptops", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("Desktops", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("Workstations", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("Servers", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("AllInOnePCs", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("GraphicsTablets", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("Software", ProductGroups.COMPUTERS);
        GROUP_BY_CATEGORY.put("Smartphones", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("StationaryPhones", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("Tablets", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("SmartphoneCases", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("ScreenProtectors", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("Chargers", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("Powerbanks", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("MobileHeadphones", ProductGroups.SMARTPHONES_TABLETS);
        GROUP_BY_CATEGORY.put("Printers", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("LaserPrinters", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("InkPrinters", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("PhotoPrinters", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("LargeFormatPrinters", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("LabelPrinters", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("Printers3D", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("Scanners", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("MultifunctionPrinters", ProductGroups.PRINTERS_SCANNERS);
        GROUP_BY_CATEGORY.put("Displays", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("Keyboards", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("Mice", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("KeyboardsAndMice", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("Headphones", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("Microphones", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("Webcams", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("Speakers", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("MousePads", ProductGroups.PERIPHERALS);
        GROUP_BY_CATEGORY.put("GamingChairs", ProductGroups.FURNITURE);
        GROUP_BY_CATEGORY.put("OfficeChairs", ProductGroups.FURNITURE);
        GROUP_BY_CATEGORY.put("GamingDesks", ProductGroups.FURNITURE);
        GROUP_BY_CATEGORY.put("OfficeDesks", ProductGroups.FURNITURE);
        GROUP_BY_CATEGORY.put("StandingDesks", ProductGroups.FURNITURE);
        GROUP_BY_CATEGORY.put("MonitorMounts", ProductGroups.FURNITURE);
        GROUP_BY_CATEGORY.put("Footrests", ProductGroups.FURNITURE);
    }

    public static final List<String> ALL = List.copyOf(GROUP_BY_CATEGORY.keySet());

    private ProductCategories() {
    }

    public static Optional<String> tryParse(String categoryKey) {
        return categoryKey != null && GROUP_BY_CATEGORY.containsKey(categoryKey)
                ? Optional.of(categoryKey)
                : Optional.empty();
    }

    public static Optional<String> groupOf(String categoryKey) {
        return Optional.ofNullable(GROUP_BY_CATEGORY.get(categoryKey));
    }
}
