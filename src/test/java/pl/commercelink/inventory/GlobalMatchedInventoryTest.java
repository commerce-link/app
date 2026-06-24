package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalMatchedInventoryTest {

    private final GlobalMatchedInventory inventory = new GlobalMatchedInventory();

    private InventoryItem item(String supplier) {
        return new InventoryItem("4711111111111", "MFN", 10.0, "PLN", 1, 1, supplier, true, true, false);
    }

    private MatchedInventory matchedWith(InventoryItem... items) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.getInventoryItems()).thenReturn(List.of(items));
        return matched;
    }

    private MatchedInventory keyed(String ean, String mfn) {
        return new MatchedInventory(new InventoryKey(ean, mfn), null, null);
    }

    @Test
    void startsEmpty() {
        // then
        assertEquals(0, inventory.size());
        assertTrue(inventory.all().isEmpty());
        assertTrue(inventory.allItems().isEmpty());
    }

    @Test
    void replaceSwapsState() {
        // when
        inventory.replace(List.of(matchedWith(item("Action"))));

        // then
        assertEquals(1, inventory.size());
        assertEquals(1, inventory.allItems().size());
    }

    @Test
    void allItemsFlattensEveryMatchedEntry() {
        // given
        inventory.replace(List.of(
                matchedWith(item("Action"), item("Wortmann")),
                matchedWith(item("Elko"))));

        // when / then
        assertEquals(3, inventory.allItems().size());
    }

    @Test
    void indexStartsEmpty() {
        // when / then
        assertTrue(inventory.index().all().isEmpty());
    }

    @Test
    void indexReflectsReplacedData() {
        // given
        MatchedInventory group = keyed("5901234567890", "MFN-1");
        inventory.replace(List.of(group));

        // when / then
        assertThat(inventory.index().findMatching(new InventoryKey("5901234567890", "OTHER"))).containsExactly(group);
    }

    @Test
    void indexIsRebuiltAfterReplace() {
        // given
        inventory.replace(List.of(keyed("5901234567890", "MFN-1")));
        inventory.index();
        MatchedInventory replacement = keyed("4000000000009", "MFN-2");
        inventory.replace(List.of(replacement));

        // when / then
        assertThat(inventory.index().all()).containsExactly(replacement);
    }
}
