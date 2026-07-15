package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import java.util.Map;
import java.util.HashMap;

public final class InventoryCategoryBridge {

    private static final Map<String, ProductCategory> PIM_LEAF_TO_INVENTORY_CATEGORY = new HashMap<>();

    static {
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Procesory", ProductCategory.CPU);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Systemy chłodzenia komputerów", ProductCategory.Cooler);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Karty graficzne", ProductCategory.GPU);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Płyty główne", ProductCategory.Motherboard);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Moduły zasilaczy", ProductCategory.PSU);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Urządzenia SSD", ProductCategory.Storage);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Moduły pamięci", ProductCategory.Memory);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Zabezpieczenia & uchwyty komputerów", ProductCategory.Case);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Płaskie kable i taśmy", ProductCategory.ModdingPC);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Częsci do obudowy do komputerów", ProductCategory.ModdingPC);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Regulatory prędkości wentylatora", ProductCategory.ModdingPC);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Systemy operacyjne", ProductCategory.Software);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Notebooki/laptopy", ProductCategory.Laptops);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Komputery osobiste/workstations", ProductCategory.Desktops);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Komputery wielofunkcyjne All-in-One", ProductCategory.AllInOnePCs);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Tablety graficzne", ProductCategory.GraphicsTablets);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Licencje na oprogramowanie i aktualizacje", ProductCategory.Software);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Oprogramowania do zarządzania dokumentami", ProductCategory.Software);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Oprogramowania multimedialne", ProductCategory.Software);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki laserowe", ProductCategory.LaserPrinters);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki atramentowe", ProductCategory.InkPrinters);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki do zdjęć", ProductCategory.PhotoPrinters);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki wielkoformatowe", ProductCategory.LargeFormatPrinters);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki wielofunkcyjne", ProductCategory.MultifunctionPrinters);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki 3D", ProductCategory.Printers3D);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Skanery", ProductCategory.Scanners);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Monitory komputerowe", ProductCategory.Displays);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Monitory do wideokonferencji", ProductCategory.Displays);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Telewizory", ProductCategory.Displays);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Wyświetlacze ścienne wideo", ProductCategory.Displays);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Tablice interaktywne", ProductCategory.Displays);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Signage Displays", ProductCategory.Displays);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury", ProductCategory.Keyboards);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury numeryczne", ProductCategory.Keyboards);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Klawiatury do urządzeń mobilnych", ProductCategory.Keyboards);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Myszki", ProductCategory.Mice);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Słuchawki i zestawy słuchawkowe", ProductCategory.Headphones);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Mikrofony", ProductCategory.Microphones);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Kamery internetowe", ProductCategory.Webcams);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Kamery do wideokonferencji", ProductCategory.Webcams);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Głośniki", ProductCategory.Speakers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Przetworniki głośnikowe", ProductCategory.Speakers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Głośniki przenośne i imprezowe", ProductCategory.Speakers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Moduły głośników", ProductCategory.Speakers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Zestawy głośników", ProductCategory.Speakers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Podkładki pod mysz", ProductCategory.MousePads);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Smartfony", ProductCategory.Smartphones);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Telefony", ProductCategory.StationaryPhones);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Telefony voip", ProductCategory.StationaryPhones);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Tablets", ProductCategory.Tablets);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Serwery", ProductCategory.Servers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Ładowarki do urządzeń przenośnych", ProductCategory.Chargers);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Uchwyty i stojaki do monitorów", ProductCategory.MonitorMounts);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Drukarki etykiet", ProductCategory.LabelPrinters);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Podnóżki", ProductCategory.Footrests);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Banki mocy", ProductCategory.Powerbanks);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Pokrowce na telefony komórkowe", ProductCategory.SmartphoneCases);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Ochraniacze na ekran i tył telefonu", ProductCategory.ScreenProtectors);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Fotele do gier", ProductCategory.GamingChairs);
        PIM_LEAF_TO_INVENTORY_CATEGORY.put("Biurka komputerowe", ProductCategory.GamingDesks);
    }

    private InventoryCategoryBridge() {
    }

    public static String toInventoryCategory(String category) {
        ProductCategory mapped = PIM_LEAF_TO_INVENTORY_CATEGORY.get(category);
        return mapped != null ? mapped.name() : category;
    }
}
