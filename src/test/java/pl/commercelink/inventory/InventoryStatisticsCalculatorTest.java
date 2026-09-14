package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class InventoryStatisticsCalculatorTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private MatchedInventory group(String ean, String mfn, InventoryItem... items) {
        return new MatchedInventory(new InventoryKey(ean, mfn), List.of(items), taxonomyCache, supplierRegistry);
    }

    private InventoryItem item(String ean, String mfn, String supplier, int qty) {
        return new InventoryItem(ean, mfn, 100.0, "PLN", qty, 1, supplier);
    }

    @Test
    void countsOnlyProductsOfferedByEnabledGlobalSuppliers() {
        // given
        InventoryIndex global = InventoryIndex.of(List.of(
                group("5900000000001", "A", item("5900000000001", "A", "Elko", 5)),
                group("5900000000002", "B", item("5900000000002", "B", "Disabled", 5))));

        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(global, Set.of("Elko")::contains, InventoryIndex.of(List.of()));

        // then
        assertThat(stats.distinctProducts()).isEqualTo(1);
        assertThat(stats.bySupplier()).containsOnlyKeys("Elko");
    }

    @Test
    void countsAProductPresentInGlobalAndOwnIndexOnce() {
        // given
        InventoryIndex global = InventoryIndex.of(List.of(
                group("5900000000001", "A", item("5900000000001", "A", "Elko", 0))));
        InventoryIndex own = InventoryIndex.of(List.of(
                group("5900000000001", "A", item("5900000000001", "A", "Kosatec", 3)),
                group("5900000000009", "Z", item("5900000000009", "Z", "Kosatec", 0))));

        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(global, Set.of("Elko")::contains, own);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(2);
        assertThat(stats.productsInStock()).isEqualTo(1);
        assertThat(stats.bySupplier().get("Kosatec")).isEqualTo(new SupplierOfferStats(2, 1));
        assertThat(stats.bySupplier().get("Elko")).isEqualTo(new SupplierOfferStats(1, 0));
    }

    @Test
    void emptyIndexesGiveZeroes() {
        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(
                InventoryIndex.of(List.of()), supplier -> true, InventoryIndex.of(List.of()));

        // then
        assertThat(stats).isEqualTo(InventoryStatistics.EMPTY);
        assertThat(stats.inStockPercent()).isZero();
    }
}
