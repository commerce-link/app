package pl.commercelink.products;


import java.util.Map;
import java.util.HashMap;

public final class InventoryCategoryBridge {

    private static final Map<String, String> PIM_LEAF_TO_INVENTORY_CATEGORY = new HashMap<>();

    static {
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Procesory", "CPU");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Systemy chłodzenia komputerów", "Cooler");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Karty graficzne", "GPU");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Płyty główne", "Motherboard");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Moduły zasilaczy", "PSU");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Urządzenia SSD", "Storage");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Moduły pamięci", "Memory");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Zabezpieczenia & uchwyty komputerów", "Case");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Płaskie kable i taśmy", "ModdingPC");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Częsci do obudowy do komputerów", "ModdingPC");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Regulatory prędkości wentylatora", "ModdingPC");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Systemy operacyjne", "Software");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Notebooki/laptopy", "Laptops");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Komputery osobiste/workstations", "Desktops");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Komputery wielofunkcyjne All-in-One", "AllInOnePCs");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Tablety graficzne", "GraphicsTablets");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Licencje na oprogramowanie i aktualizacje", "Software");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Oprogramowania do zarządzania dokumentami", "Software");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Oprogramowania multimedialne", "Software");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki laserowe", "LaserPrinters");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki atramentowe", "InkPrinters");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki do zdjęć", "PhotoPrinters");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki wielkoformatowe", "LargeFormatPrinters");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki wielofunkcyjne", "MultifunctionPrinters");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki 3D", "Printers3D");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Skanery", "Scanners");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Monitory komputerowe", "Displays");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Monitory do wideokonferencji", "Displays");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Telewizory", "Displays");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Wyświetlacze ścienne wideo", "Displays");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Tablice interaktywne", "Displays");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Signage Displays", "Displays");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury", "Keyboards");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury numeryczne", "Keyboards");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury do urządzeń mobilnych", "Keyboards");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Myszki", "Mice");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Słuchawki i zestawy słuchawkowe", "Headphones");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Mikrofony", "Microphones");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Kamery internetowe", "Webcams");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Kamery do wideokonferencji", "Webcams");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Głośniki", "Speakers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Przetworniki głośnikowe", "Speakers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Głośniki przenośne i imprezowe", "Speakers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Moduły głośników", "Speakers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Zestawy głośników", "Speakers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Podkładki pod mysz", "MousePads");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Smartfony", "Smartphones");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Telefony", "StationaryPhones");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Telefony voip", "StationaryPhones");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Tablets", "Tablets");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Serwery", "Servers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Ładowarki do urządzeń przenośnych", "Chargers");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Uchwyty i stojaki do monitorów", "MonitorMounts");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki etykiet", "LabelPrinters");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Podnóżki", "Footrests");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Banki mocy", "Powerbanks");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Pokrowce na telefony komórkowe", "SmartphoneCases");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Ochraniacze na ekran i tył telefonu", "ScreenProtectors");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Fotele do gier", "GamingChairs");
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Biurka komputerowe", "GamingDesks");
    }

    private InventoryCategoryBridge() {
    }

    public static String toInventoryCategory(String category) {
        String mapped = PIM_LEAF_TO_INVENTORY_CATEGORY.get(category);
        return mapped != null ? mapped : category;
    }
}
