package pl.commercelink.inventory;

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
        InventoryKey catalogKey = new InventoryKey();
        catalogKey.merge(key);
        if (ean != null && !ean.isBlank()) {
            catalogKey.addEan(ean);
        }
        if (mfn != null && !mfn.isBlank()) {
            catalogKey.addManufacturerCode(mfn);
        }
        return catalogKey;
    }
}
