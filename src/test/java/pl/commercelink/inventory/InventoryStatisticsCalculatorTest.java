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

    // store entries in these tests carry an "-s-" id; every other id is a global PIM entry
    private static final java.util.function.Predicate<String> STORE_ENTRY = id -> id.contains("-s-");

    private MatchedInventory group(String ean, String mfn, InventoryItem... items) {
        return new MatchedInventory(new InventoryKey(ean, mfn), List.of(items), taxonomyCache, supplierRegistry);
    }

    private MatchedInventory pimGroup(String id, String ean, String mfn, InventoryItem... items) {
        InventoryKey key = new InventoryKey(id);
        key.addEan(ean);
        key.addManufacturerCode(mfn);
        return new MatchedInventory(key, List.of(items), taxonomyCache, supplierRegistry);
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
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(global, Set.of("Elko")::contains, InventoryIndex.of(List.of()), STORE_ENTRY);

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
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(global, Set.of("Elko")::contains, own, STORE_ENTRY);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(2);
        assertThat(stats.productsInStock()).isEqualTo(1);
        assertThat(stats.bySupplier().get("Kosatec")).isEqualTo(new SupplierOfferStats(2, 1));
        assertThat(stats.bySupplier().get("Elko")).isEqualTo(new SupplierOfferStats(1, 0));
    }

    @Test
    void ownGroupMatchingTwoGlobalGroupsJoinsOnlyTheFirstOne() {
        // given
        InventoryIndex global = InventoryIndex.of(List.of(
                group("5900000000001", "A", item("5900000000001", "A", "Elko", 5)),
                group("5900000000002", "B", item("5900000000002", "B", "Elko", 5))));
        InventoryIndex own = InventoryIndex.of(List.of(
                group("5900000000001", "B", item("5900000000001", "B", "Kosatec", 3))));

        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(global, Set.of("Elko")::contains, own, STORE_ENTRY);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(2);
        assertThat(stats.bySupplier().get("Kosatec")).isEqualTo(new SupplierOfferStats(1, 1));
    }

    @Test
    void emptyIndexesGiveZeroes() {
        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(
                InventoryIndex.of(List.of()), supplier -> true, InventoryIndex.of(List.of()), STORE_ENTRY);

        // then
        assertThat(stats).isEqualTo(InventoryStatistics.EMPTY);
        assertThat(stats.inStockPercent()).isZero();
    }

    @Test
    void storeEntryGroupCountsAsADistinctProduct() {
        // given
        MatchedInventory globalGroup = pimGroup("e3-g-c", "5900000000031", "E3-C-S", item("5900000000031", "E3-C-S", "Elko", 5));
        MatchedInventory ownGroup = pimGroup("e3-s-c", "5900000000032", "E3-C-S", item("5900000000032", "E3-C-S", "Kosatec", 3));

        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(InventoryIndex.of(List.of(globalGroup)),
                Set.of("Elko")::contains, InventoryIndex.of(List.of(ownGroup)), STORE_ENTRY);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(2);
        assertThat(stats.bySupplier().get("Kosatec")).isEqualTo(new SupplierOfferStats(1, 1));
    }

    @Test
    void ownGroupWithAGlobalPimIdJoinsAGlobalGroupThatLostItsIdByPriceSplit() {
        // given
        MatchedInventory splitGlobalGroup = group("5900000000041", "G4", item("5900000000041", "G4", "Elko", 5));
        MatchedInventory ownGroup = pimGroup("g4", "5900000000041", "G4", item("5900000000041", "G4", "Kosatec", 3));

        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(InventoryIndex.of(List.of(splitGlobalGroup)),
                Set.of("Elko")::contains, InventoryIndex.of(List.of(ownGroup)), STORE_ENTRY);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(1);
        assertThat(stats.bySupplier().get("Kosatec")).isEqualTo(new SupplierOfferStats(1, 1));
        assertThat(stats.bySupplier().get("Elko")).isEqualTo(new SupplierOfferStats(1, 1));
    }

    @Test
    void ownGroupWithTheGlobalPimIdIsMergedIntoTheGlobalGroup() {
        // given
        MatchedInventory globalGroup = pimGroup("g1", "5900000000031", "G1", item("5900000000031", "G1", "Elko", 0));
        MatchedInventory ownGroup = pimGroup("g1", "5900000000031", "G1", item("5900000000031", "G1", "Kosatec", 3));

        // when
        InventoryStatistics stats = InventoryStatisticsCalculator.calculate(InventoryIndex.of(List.of(globalGroup)),
                Set.of("Elko")::contains, InventoryIndex.of(List.of(ownGroup)), STORE_ENTRY);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(1);
        assertThat(stats.productsInStock()).isEqualTo(1);
        assertThat(stats.bySupplier().get("Kosatec")).isEqualTo(new SupplierOfferStats(1, 1));
    }
}
