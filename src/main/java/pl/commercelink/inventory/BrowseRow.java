package pl.commercelink.inventory;

import org.springframework.lang.Nullable;

/** One product of a browse page, priced from the offers the store can buy (delivered cost of one unit to Poland). */
public record BrowseRow(InventoryKey key, String name, String brand, String ean, String mfn, String categoryId,
                        String categoryText, double lowestDeliveredNet, boolean deliveryKnown, String lowestSupplier,
                        long qty, int suppliers) {

    /**
     * The EANs a catalog product of this row can carry: the group's and the one the row shows and adds by. They differ
     * when a store's own offer joined the group and is the cheapest. Built on demand, for the rows of a page.
     */
    public InventoryKey catalogKey() {
        return catalogKey(key, ean);
    }

    /** The same key for a product picked by one of its offers' EANs: the add dialog must count what the row counts. */
    public static InventoryKey catalogKey(InventoryKey group, @Nullable String ean) {
        InventoryKey catalogKey = new InventoryKey();
        catalogKey.merge(group);
        if (ean != null && !ean.isBlank()) {
            catalogKey.addEan(ean);
        }
        return catalogKey;
    }
}
