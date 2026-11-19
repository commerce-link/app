package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.ProductDataResolver;

import java.util.List;

/** Real views over hand-built groups, for tests of other packages that need lookups to behave as in production. */
public final class InventoryViewFixture {

    private InventoryViewFixture() {
    }

    /** A view over global groups only, all suppliers enabled; {@code storeId} null gives the global view. */
    public static InventoryView over(String storeId, ProductDataResolver resolver, SupplierRegistry supplierRegistry,
                                     MatchedInventory... globalGroups) {
        InventoryIndex globalIndex = InventoryIndex.of(List.of(globalGroups));
        return new InventoryView(storeId, resolver, globalIndex, InventoryIndex.of(List.of()), StoreEntryKeys.none(),
                supplierRegistry, GroupInventorySource.global(globalIndex, supplier -> true));
    }
}
