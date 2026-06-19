package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalMatchedInventoryTest {

    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);

    private MatchedInventory group(String ean, String mfn, String supplier) {
        InventoryItem item = new InventoryItem(ean, mfn, 100.0, "PLN", 5, 2, supplier, true, true, false);
        return new MatchedInventory(new InventoryKey(ean, mfn), List.of(item), taxonomyCache, supplierRegistry);
    }

    @Test
    void findsCandidateByEan() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", "Action"), group("222", "BBB", "Asbis")));

        // when
        var candidates = global.candidatesFor(InventoryKey.fromEan("111"));

        // then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.iterator().next().getEans()).contains("111");
    }

    @Test
    void findsCandidateByMfn() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", "Action")));

        // when
        var candidates = global.candidatesFor(InventoryKey.fromMfn("AAA"));

        // then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.iterator().next().getMfnCodes()).contains("AAA");
    }

    @Test
    void findsCandidateById() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        InventoryKey keyWithId = InventoryKey.fromPimId("PIM-1");
        MatchedInventory groupWithId = new MatchedInventory(keyWithId, List.of(
                new InventoryItem("111", "AAA", 100.0, "PLN", 5, 2, "Action", true, true, false)
        ), taxonomyCache, supplierRegistry);
        global.replace(List.of(groupWithId));

        // when
        var candidates = global.candidatesFor(InventoryKey.fromPimId("PIM-1"));

        // then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.iterator().next().getInventoryKey().getId()).isEqualTo("PIM-1");
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", "Action")));

        // when
        var candidates = global.candidatesFor(InventoryKey.fromEan("999"));

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    void rebuildsIndexOnReplace() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", "Action")));

        // when
        global.replace(List.of(group("222", "BBB", "Asbis")));

        // then
        assertThat(global.candidatesFor(InventoryKey.fromEan("111"))).isEmpty();
        assertThat(global.candidatesFor(InventoryKey.fromEan("222"))).hasSize(1);
    }

    @Test
    void allItemsFlattensItemsAcrossGroups() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", "Action"), group("222", "BBB", "Asbis")));

        // when
        var items = global.allItems();

        // then
        assertThat(items).hasSize(2);
    }

    @Test
    void sizeReturnsNumberOfGroups() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", "Action"), group("222", "BBB", "Asbis")));

        // when / then
        assertThat(global.size()).isEqualTo(2);
    }

    @Test
    void allReflectsReplacedCollection() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        var first = List.of(group("111", "AAA", "Action"));
        var second = List.of(group("222", "BBB", "Asbis"));
        global.replace(first);

        // when
        global.replace(second);

        // then
        assertThat(global.all()).isEqualTo(second);
    }
}
