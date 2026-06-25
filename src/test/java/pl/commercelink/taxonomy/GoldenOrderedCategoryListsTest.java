package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GoldenOrderedCategoryListsTest {

    @Test
    void valuesDeclarationOrderFreezesOtherAtOrdinalTen() {
        // given
        ProductCategory[] values = ProductCategory.values();

        // then
        // Declaration order == ordinal. Other sits at ordinal 10 (NOT at the end),
        // immediately followed by Services at ordinal 11 and Laptops at ordinal 12.
        assertThat(values[10]).isEqualTo(ProductCategory.Other);
        assertThat(values[11]).isEqualTo(ProductCategory.Services);
        assertThat(values[12]).isEqualTo(ProductCategory.Laptops);
        assertThat(values[13]).isEqualTo(ProductCategory.Desktops);
        assertThat(ProductCategory.Other.ordinal()).isEqualTo(10);
        assertThat(ProductCategory.Services.ordinal()).isEqualTo(11);
    }

    @Test
    void fullValuesListFreezesAllFiftyTwoNamesInDeclarationOrder() {
        // given
        List<String> names = new ArrayList<>();
        for (ProductCategory value : ProductCategory.values()) {
            names.add(value.name());
        }

        // then
        assertThat(names).hasSize(52);
        assertThat(names).containsExactly(
                "CPU", "Cooler", "GPU", "Motherboard", "PSU", "Storage", "Memory",
                "Case", "Fan", "ModdingPC", "Other",
                "Services",
                "Laptops", "Desktops", "Workstations", "Servers", "AllInOnePCs",
                "GraphicsTablets", "Software",
                "Smartphones", "StationaryPhones", "Tablets", "SmartphoneCases",
                "ScreenProtectors", "Chargers", "Powerbanks", "MobileHeadphones",
                "Printers", "LaserPrinters", "InkPrinters", "PhotoPrinters",
                "LargeFormatPrinters", "LabelPrinters", "Printers3D", "Scanners",
                "MultifunctionPrinters",
                "Displays", "Keyboards", "Mice", "KeyboardsAndMice", "Headphones",
                "Microphones", "Webcams", "Speakers", "MousePads",
                "GamingChairs", "OfficeChairs", "GamingDesks", "OfficeDesks",
                "StandingDesks", "MonitorMounts", "Footrests"
        );
    }

    @Test
    void naturalCategoryComparatorPlacesOtherInTheMiddleNotLast() {
        // given
        // WarehouseController (sorted(Comparator.comparing(WarehouseItem::getCategory)))
        // and StockLevels (sorted(Comparator.comparing(StockProductLevel::getCategory)))
        // both sort by the ProductCategory enum's natural ordering == ordinal.
        // We reproduce that exact comparator against a representative shuffled list.
        List<ProductCategory> representative = new ArrayList<>(List.of(
                ProductCategory.Footrests,
                ProductCategory.Other,
                ProductCategory.Services,
                ProductCategory.Laptops,
                ProductCategory.CPU,
                ProductCategory.Printers
        ));

        // when
        representative.sort(Comparator.naturalOrder());

        // then
        // Natural (ordinal) order: CPU(0) < Other(10) < Services(11) < Laptops(12) < Printers(27) < Footrests(51).
        // Other lands in the MIDDLE by ordinal, NOT alphabetically and NOT last.
        assertThat(representative).containsExactly(
                ProductCategory.CPU,
                ProductCategory.Other,
                ProductCategory.Services,
                ProductCategory.Laptops,
                ProductCategory.Printers,
                ProductCategory.Footrests
        );
        // Other precedes Services/Laptops/Printers/Footrests purely by ordinal.
        assertThat(representative.indexOf(ProductCategory.Other))
                .isLessThan(representative.indexOf(ProductCategory.Services));
        assertThat(representative.indexOf(ProductCategory.Other))
                .isNotEqualTo(representative.size() - 1);
    }

    @Test
    void comparingByCategoryGetterMatchesProductionComparatorOrdering() {
        // given
        // Exercise the production comparator shape directly: Comparator.comparing(getter)
        // where the getter yields the ProductCategory enum (as in WarehouseItem/StockProductLevel).
        record CategoryHolder(ProductCategory category) {
            ProductCategory getCategory() {
                return category;
            }
        }
        List<CategoryHolder> holders = new ArrayList<>(List.of(
                new CategoryHolder(ProductCategory.Footrests),
                new CategoryHolder(ProductCategory.Other),
                new CategoryHolder(ProductCategory.Laptops),
                new CategoryHolder(ProductCategory.CPU)
        ));

        // when
        holders.sort(Comparator.comparing(CategoryHolder::getCategory));

        // then
        assertThat(holders.stream().map(CategoryHolder::getCategory).toList())
                .containsExactly(
                        ProductCategory.CPU,
                        ProductCategory.Other,
                        ProductCategory.Laptops,
                        ProductCategory.Footrests
                );
    }
}
