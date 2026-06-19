package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CompositeMatchedInventorySourceTest {

    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);

    private InventoryItem item(String ean, String mfn, String supplier) {
        return new InventoryItem(ean, mfn, 100.0, "PLN", 5, 2, supplier, true, true, false);
    }

    private MatchedInventory group(String ean, String mfn, InventoryItem... items) {
        return new MatchedInventory(new InventoryKey(ean, mfn), List.of(items), taxonomyCache, supplierRegistry);
    }

    @Test
    void coalescesSameProductFromOwnAndGlobalIntoOneCandidate() {
        // given
        MatchedInventory own = group("111", "AAA", item("111", "AAA", "ActionOwn"));
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", item("111", "AAA", "Asbis"))));
        CompositeMatchedInventorySource source = new CompositeMatchedInventorySource(
                List.of(own), global, Set.of("Asbis"), taxonomyCache, supplierRegistry);

        // when
        Collection<MatchedInventory> candidates = source.candidatesFor(InventoryKey.fromEan("111"));

        // then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.iterator().next().getSuppliers())
                .containsExactlyInAnyOrder("ActionOwn", "Asbis");
    }

    @Test
    void excludesGlobalSupplierNotInAllowedSet() {
        // given
        MatchedInventory own = group("111", "AAA", item("111", "AAA", "ActionOwn"));
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "AAA", item("111", "AAA", "Action"))));
        CompositeMatchedInventorySource source = new CompositeMatchedInventorySource(
                List.of(own), global, Set.of(), taxonomyCache, supplierRegistry);

        // when
        Collection<MatchedInventory> candidates = source.candidatesFor(InventoryKey.fromEan("111"));

        // then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.iterator().next().getSuppliers()).containsExactly("ActionOwn");
    }

    @Test
    void keepsDistinctProductsSeparate() {
        // given
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(
                group("111", "AAA", item("111", "AAA", "Asbis")),
                group("222", "BBB", item("222", "BBB", "Asbis"))));
        CompositeMatchedInventorySource source = new CompositeMatchedInventorySource(
                List.of(), global, Set.of("Asbis"), taxonomyCache, supplierRegistry);

        // when
        Collection<MatchedInventory> all = source.all();

        // then
        assertThat(all).hasSize(2);
    }

    @Test
    void coalescesMergesTransitiveBridgeGroupIntoSingleCandidate() {
        // given
        MatchedInventory ownA = group("111", "AAA", item("111", "AAA", "SupA"));
        MatchedInventory ownB = group("222", "BBB", item("222", "BBB", "SupB"));
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(group("111", "BBB", item("111", "BBB", "SupC"))));
        CompositeMatchedInventorySource source = new CompositeMatchedInventorySource(
                List.of(ownA, ownB), global, Set.of("SupC"), taxonomyCache, supplierRegistry);

        // when
        Collection<MatchedInventory> candidates = source.candidatesFor(new InventoryKey("111", "BBB"));

        // then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.iterator().next().getSuppliers())
                .containsExactlyInAnyOrder("SupA", "SupB", "SupC");
    }

    @Test
    void allCoalescesOwnWithMatchingGlobalAndKeepsUnmatchedGlobal() {
        // given
        MatchedInventory own = group("111", "AAA", item("111", "AAA", "ActionOwn"));
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(
                group("111", "AAA", item("111", "AAA", "Asbis")),
                group("333", "CCC", item("333", "CCC", "Asbis"))));
        CompositeMatchedInventorySource source = new CompositeMatchedInventorySource(
                List.of(own), global, Set.of("Asbis"), taxonomyCache, supplierRegistry);

        // when
        Collection<MatchedInventory> all = source.all();

        // then
        assertThat(all).hasSize(2);
        assertThat(all.stream().anyMatch(m -> m.getSuppliers().containsAll(Set.of("ActionOwn", "Asbis")))).isTrue();
        assertThat(all.stream().anyMatch(m -> m.getEans().contains("333"))).isTrue();
    }
}
