package pl.commercelink.inventory;

import org.springframework.lang.Nullable;

/** One product of a browse page, priced from the offers the store can buy (delivered cost of one unit to Poland). */
public record BrowseRow(InventoryKey key, String name, String brand, String ean, String mfn, String categoryId,
                        String categoryText, double lowestDeliveredNet, boolean deliveryKnown, String lowestSupplier,
                        long qty, int suppliers) {

    /**
     * The codes a catalog product of this row can carry: the group's and the ones the row shows and adds by. They differ
     * when a store's own offer joined the group by one shared code and is the cheapest. Built on demand, for the rows of
     * a page.
     */
    public InventoryKey catalogKey() {
        return catalogKey(key, ean, mfn);
    }

    /** The same key for a product picked by one of its offers' codes: the add dialog must count what the row counts. */
    public static InventoryKey catalogKey(InventoryKey group, @Nullable String ean, @Nullable String mfn) {
        InventoryKey catalogKey = new InventoryKey();
        catalogKey.merge(group);
        if (ean != null && !ean.isBlank()) {
            catalogKey.addEan(ean);
        }
        if (mfn != null && !mfn.isBlank()) {
            catalogKey.addManufacturerCode(mfn);
        }
        return catalogKey;
    }
}
