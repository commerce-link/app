package pl.commercelink.inventory;

/** One product of a browse page, priced from the offers the store can buy (delivered cost of one unit to Poland). */
public record BrowseRow(InventoryKey key, String name, String brand, String ean, String mfn, String categoryId,
                        String categoryText, double lowestDeliveredNet, boolean deliveryKnown, String lowestSupplier,
                        long qty, int suppliers) {
}
