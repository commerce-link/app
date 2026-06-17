package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.List;

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

    @Test
    void startsEmpty() {
        assertEquals(0, inventory.size());
        assertTrue(inventory.all().isEmpty());
        assertTrue(inventory.allItems().isEmpty());
    }

    @Test
    void replaceSwapsState() {
        inventory.replace(List.of(matchedWith(item("Action"))));

        assertEquals(1, inventory.size());
        assertEquals(1, inventory.allItems().size());
    }

    @Test
    void itemsForSuppliersReturnsOnlyRequestedSuppliers() {
        inventory.replace(List.of(
                matchedWith(item("Action"), item("Wortmann")),
                matchedWith(item("Elko"))));

        List<InventoryItem> result = inventory.itemsForSuppliers(List.of("Action", "Elko"));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(i -> List.of("Action", "Elko").contains(i.supplier())));
    }

    @Test
    void allItemsFlattensEveryMatchedEntry() {
        inventory.replace(List.of(
                matchedWith(item("Action"), item("Wortmann")),
                matchedWith(item("Elko"))));

        assertEquals(3, inventory.allItems().size());
    }
}
