package pl.commercelink.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
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
            String sku,
            String brand
    ) {
        static ItemData from(InventoryItem item) {
            return new ItemData(item.ean(), item.mfn(), item.netPrice(), item.currency(),
                    item.qty(), item.leadTimeDays(), item.supplier(),
                    item.sellable(), item.inStock(), item.inDelivery(), item.sku(), item.brand());
        }

        InventoryItem toInventoryItem() {
            return new InventoryItem(brand, sku, ean, mfn, supplier, netPrice, currency, qty, leadTimeDays,
                    sellable, inStock, inDelivery);
        }
    }

    /** {@code linkedEans}: the link of an own group to a global group, absent (null) in older snapshots. */
    public record Entry(String id, InventoryKey key, List<ItemData> items,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> linkedEans) {
    }

    public static StoreInventorySnapshot from(StoreInventory inventory) {
        List<Entry> entries = new ArrayList<>();
        for (MatchedInventory matched : inventory.items()) {
            List<ItemData> items = matched.getInventoryItems().stream()
                    .map(ItemData::from)
                    .toList();
            InventoryKey key = matched.getInventoryKey();
            entries.add(new Entry(key.getId(), key, items, List.copyOf(key.getLinkedEans())));
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
        if (entry.linkedEans() != null && !entry.linkedEans().isEmpty()) {
            key.linkTo(entry.linkedEans());
        }
        return key;
    }
}
