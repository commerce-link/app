package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import java.util.Map;
import java.util.HashMap;

public final class InventoryCategoryBridge {

    private static final Map<String, ProductCategory> ICECAT_LEAF_TO_INVENTORY_CATEGORY = new HashMap<>();

    static {
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Procesory", ProductCategory.CPU);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Systemy chłodzenia komputerów", ProductCategory.Cooler);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Karty graficzne", ProductCategory.GPU);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Płyty główne", ProductCategory.Motherboard);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Moduły zasilaczy", ProductCategory.PSU);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Urządzenia SSD", ProductCategory.Storage);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Moduły pamięci", ProductCategory.Memory);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Zabezpieczenia & uchwyty komputerów", ProductCategory.Case);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Płaskie kable i taśmy", ProductCategory.ModdingPC);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Częsci do obudowy do komputerów", ProductCategory.ModdingPC);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Regulatory prędkości wentylatora", ProductCategory.ModdingPC);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Systemy operacyjne", ProductCategory.Software);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Notebooki/laptopy", ProductCategory.Laptops);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Komputery osobiste/workstations", ProductCategory.Desktops);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Komputery wielofunkcyjne All-in-One", ProductCategory.AllInOnePCs);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Tablety graficzne", ProductCategory.GraphicsTablets);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Licencje na oprogramowanie i aktualizacje", ProductCategory.Software);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Oprogramowania do zarządzania dokumentami", ProductCategory.Software);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Oprogramowania multimedialne", ProductCategory.Software);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki laserowe", ProductCategory.LaserPrinters);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki atramentowe", ProductCategory.InkPrinters);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki do zdjęć", ProductCategory.PhotoPrinters);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki wielkoformatowe", ProductCategory.LargeFormatPrinters);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki wielofunkcyjne", ProductCategory.MultifunctionPrinters);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki 3D", ProductCategory.Printers3D);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Skanery", ProductCategory.Scanners);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Monitory komputerowe", ProductCategory.Displays);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Monitory do wideokonferencji", ProductCategory.Displays);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Telewizory", ProductCategory.Displays);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Wyświetlacze ścienne wideo", ProductCategory.Displays);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Tablice interaktywne", ProductCategory.Displays);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Signage Displays", ProductCategory.Displays);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury", ProductCategory.Keyboards);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury numeryczne", ProductCategory.Keyboards);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury do urządzeń mobilnych", ProductCategory.Keyboards);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Myszki", ProductCategory.Mice);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Słuchawki i zestawy słuchawkowe", ProductCategory.Headphones);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Mikrofony", ProductCategory.Microphones);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Kamery internetowe", ProductCategory.Webcams);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Kamery do wideokonferencji", ProductCategory.Webcams);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Głośniki", ProductCategory.Speakers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Przetworniki głośnikowe", ProductCategory.Speakers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Głośniki przenośne i imprezowe", ProductCategory.Speakers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Moduły głośników", ProductCategory.Speakers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Zestawy głośników", ProductCategory.Speakers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Podkładki pod mysz", ProductCategory.MousePads);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Smartfony", ProductCategory.Smartphones);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Telefony", ProductCategory.StationaryPhones);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Telefony voip", ProductCategory.StationaryPhones);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Tablets", ProductCategory.Tablets);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Serwery", ProductCategory.Servers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Ładowarki do urządzeń przenośnych", ProductCategory.Chargers);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Uchwyty i stojaki do monitorów", ProductCategory.MonitorMounts);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki etykiet", ProductCategory.LabelPrinters);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Podnóżki", ProductCategory.Footrests);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Banki mocy", ProductCategory.Powerbanks);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Pokrowce na telefony komórkowe", ProductCategory.SmartphoneCases);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Ochraniacze na ekran i tył telefonu", ProductCategory.ScreenProtectors);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Fotele do gier", ProductCategory.GamingChairs);
        ICECAT_LEAF_TO_INVENTORY_CATEGORY.put("Biurka komputerowe", ProductCategory.GamingDesks);
    }

    private InventoryCategoryBridge() {
    }

    public static String toInventoryCategory(String category) {
        ProductCategory mapped = ICECAT_LEAF_TO_INVENTORY_CATEGORY.get(category);
        return mapped != null ? mapped.name() : category;
    }
}
