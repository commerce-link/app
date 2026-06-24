package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class InventoryIndexTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private MatchedInventory group(InventoryKey key) {
        return new MatchedInventory(key, taxonomyCache, supplierRegistry);
    }

    @Test
    void findsGroupByEan() {
        // given
        MatchedInventory matched = group(new InventoryKey("5901234567890", "MFN-1"));
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("5901234567890", "OTHER"))).containsExactly(matched);
    }

    @Test
    void findsGroupByProductCode() {
        // given
        MatchedInventory matched = group(new InventoryKey("5901234567890", "MFN-1"));
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("0000000000000", "MFN-1"))).containsExactly(matched);
    }

    @Test
    void findsGroupByPimId() {
        // given
        MatchedInventory matched = group(new InventoryKey("PIM-1"));
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("PIM-1"))).containsExactly(matched);
    }

    @Test
    void returnsEachMatchingGroupOnce() {
        // given
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");
        MatchedInventory matched = group(key);
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("5901234567890", "MFN-1"))).containsExactly(matched);
    }

    @Test
    void returnsAllGroupsSharingTheLookupCode() {
        // given
        MatchedInventory first = group(new InventoryKey("5901234567890", "MFN-1"));
        MatchedInventory second = group(new InventoryKey("4000000000009", "MFN-1"));
        InventoryIndex index = InventoryIndex.of(List.of(first, second));

        // when / then
        assertThat(index.findMatching(new InventoryKey("0000000000000", "MFN-1"))).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        // given
        InventoryIndex index = InventoryIndex.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(index.findMatching(new InventoryKey("0000000000000", "OTHER"))).isEmpty();
    }

    @Test
    void exposesIndexedGroupsInOrder() {
        // given
        MatchedInventory first = group(new InventoryKey("5901234567890", "MFN-1"));
        MatchedInventory second = group(new InventoryKey("4000000000009", "MFN-2"));
        InventoryIndex index = InventoryIndex.of(List.of(first, second));

        // when / then
        assertThat(index.all()).containsExactly(first, second);
    }

    @Test
    void containsReflectsWhetherAnyGroupMatches() {
        // given
        InventoryIndex index = InventoryIndex.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(index.contains(new InventoryKey("5901234567890", "OTHER"))).isTrue();
        assertThat(index.contains(new InventoryKey("0000000000000", "OTHER"))).isFalse();
    }
}
