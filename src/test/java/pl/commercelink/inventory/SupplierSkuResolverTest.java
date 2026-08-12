package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.SupplierScope;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierSkuResolverTest {

    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView view;
    @Mock
    private MatchedInventory matched;

    @InjectMocks
    private SupplierSkuResolver resolver;

    @Test
    void prefersExactEanAndMfnMatch() {
        // given
        when(inventory.withEnabledSuppliersOnly("store-1", SupplierScope.FULFILMENT)).thenReturn(view);
        when(view.findByInventoryKey(any())).thenReturn(matched);
        when(matched.getInventoryItemsFromSupplier("Elko")).thenReturn(List.of(
                item("4006381333931", "MFN-OTHER", "202"),
                item("4006381333931", "MFN-A", "101")));
        // when / then
        assertEquals("101", resolver.forStore("store-1", "Elko").skuFor("4006381333931", "MFN-A"));
    }

    @Test
    void fallsBackToEanOnlyMatch() {
        // given
        when(inventory.withEnabledSuppliersOnly("store-1", SupplierScope.FULFILMENT)).thenReturn(view);
        when(view.findByInventoryKey(any())).thenReturn(matched);
        when(matched.getInventoryItemsFromSupplier("Elko")).thenReturn(List.of(
                item("4006381333931", "MFN-OTHER", "202")));
        // when / then
        assertEquals("202", resolver.forStore("store-1", "Elko").skuFor("4006381333931", "MFN-A"));
    }

    @Test
    void returnsNullWhenSupplierHasNoMatchingItem() {
        // given
        when(inventory.withEnabledSuppliersOnly("store-1", SupplierScope.FULFILMENT)).thenReturn(view);
        when(view.findByInventoryKey(any())).thenReturn(matched);
        when(matched.getInventoryItemsFromSupplier("Elko")).thenReturn(List.of());
        // when / then
        assertNull(resolver.forStore("store-1", "Elko").skuFor("9999999999990", "TCK-UNKNOWN"));
    }

    @Test
    void skipsItemsWithoutSku() {
        // given
        when(inventory.withEnabledSuppliersOnly("store-1", SupplierScope.FULFILMENT)).thenReturn(view);
        when(view.findByInventoryKey(any())).thenReturn(matched);
        when(matched.getInventoryItemsFromSupplier("Elko")).thenReturn(List.of(
                item("4006381333931", "MFN-A", null)));
        // when / then
        assertNull(resolver.forStore("store-1", "Elko").skuFor("4006381333931", "MFN-A"));
    }

    private static InventoryItem item(String ean, String mfn, String sku) {
        return new InventoryItem(ean, mfn, 10.0, "PLN", 5, 1, "Elko").withSku(sku);
    }
}
