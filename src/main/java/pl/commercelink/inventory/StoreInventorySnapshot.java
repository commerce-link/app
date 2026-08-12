package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record StoreInventorySnapshot(LocalDateTime builtAt, List<Entry> entries) {

    public record ItemData(
            String ean,
            String mfn,
            double netPrice,
            String currency,
            int qty,
            int leadTimeDays,
            String supplier,
            boolean sellable,
            boolean inStock,
            boolean inDelivery,
            String sku
    ) {
        static ItemData from(InventoryItem item) {
            return new ItemData(item.ean(), item.mfn(), item.netPrice(), item.currency(),
                    item.qty(), item.leadTimeDays(), item.supplier(),
                    item.sellable(), item.inStock(), item.inDelivery(), item.sku());
        }

        InventoryItem toInventoryItem() {
            return new InventoryItem(ean, mfn, netPrice, currency, qty, leadTimeDays, supplier, sellable, inStock, inDelivery, sku);
        }
    }

    public record Entry(String id, InventoryKey key, List<ItemData> items) {
    }

    public static StoreInventorySnapshot from(StoreInventory inventory) {
        List<Entry> entries = new ArrayList<>();
        for (MatchedInventory matched : inventory.items()) {
            List<ItemData> items = matched.getInventoryItems().stream()
                    .map(ItemData::from)
                    .toList();
            entries.add(new Entry(matched.getInventoryKey().getId(), matched.getInventoryKey(), items));
        }
        return new StoreInventorySnapshot(inventory.builtAt(), entries);
    }

    public StoreInventory toStoreInventory(TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        Collection<MatchedInventory> matched = new ArrayList<>();
        for (Entry entry : entries) {
            List<InventoryItem> items = entry.items().stream()
                    .map(ItemData::toInventoryItem)
                    .toList();
            matched.add(new MatchedInventory(restoreKey(entry), items, taxonomyCache, supplierRegistry));
        }
        return new StoreInventory(InventoryIndex.of(matched), builtAt);
    }

    private static InventoryKey restoreKey(Entry entry) {
        InventoryKey key = new InventoryKey(entry.id());
        entry.key().getProductEans().forEach(key::addEan);
        entry.key().getProductCodes().forEach(key::addManufacturerCode);
        return key;
    }
}
