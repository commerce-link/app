package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.Taxonomy;

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

    public record ProductData(
            String ean,
            String mfn,
            String brand,
            String name,
            String category,
            String categoryId,
            int dataAccuracyScore
    ) {
        static ProductData from(Taxonomy taxonomy) {
            if (taxonomy == null || taxonomy == Taxonomy.EMPTY) {
                return null;
            }
            return new ProductData(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                    taxonomy.category(), taxonomy.categoryId(), taxonomy.dataAccuracyScore());
        }

        Taxonomy toTaxonomy() {
            return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore, null, null, null, categoryId);
        }
    }

    public record Entry(String id, InventoryKey key, ProductData product, List<ItemData> items) {
    }

    public static StoreInventorySnapshot from(StoreInventory inventory) {
        List<Entry> entries = new ArrayList<>();
        for (MatchedInventory matched : inventory.items()) {
            List<ItemData> items = matched.getInventoryItems().stream()
                    .map(ItemData::from)
                    .toList();
            entries.add(new Entry(matched.getInventoryKey().getId(), matched.getInventoryKey(),
                    ProductData.from(matched.getTaxonomy()), items));
        }
        return new StoreInventorySnapshot(inventory.builtAt(), entries);
    }

    public StoreInventory toStoreInventory(SupplierRegistry supplierRegistry) {
        Collection<MatchedInventory> matched = new ArrayList<>();
        for (Entry entry : entries) {
            List<InventoryItem> items = entry.items().stream()
                    .map(ItemData::toInventoryItem)
                    .toList();
            MatchedInventory group = new MatchedInventory(restoreKey(entry), items, supplierRegistry);
            if (entry.product() != null) {
                group.adoptTaxonomy(entry.product().toTaxonomy());
            }
            matched.add(group);
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
