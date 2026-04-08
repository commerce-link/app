package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;
import java.util.stream.Collectors;

class WarehouseInventoryFilter implements InventoryFilter {

    private final String storeId;
    private final StockQueryService stockQueryService;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    WarehouseInventoryFilter(String storeId, StockQueryService stockQueryService, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.storeId = storeId;
        this.stockQueryService = stockQueryService;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public MatchedInventory apply(MatchedInventory matched) {
        InventoryKey inventoryKey = matched.getInventoryKey().copy();

        MatchedInventory result = new MatchedInventory(inventoryKey, matched.getInventoryItems(), taxonomyCache, supplierRegistry);
        for (InventoryItem warehouseItem : fetchWarehouseInventoryItems(inventoryKey)) {
            result.addAlternativeInventoryItem(warehouseItem);
        }
        return result;
    }

    private List<InventoryItem> fetchWarehouseInventoryItems(InventoryKey inventoryKey) {
        return stockQueryService.searchAvailableByMfns(storeId, inventoryKey.getProductCodes()).stream()
                .map(WarehouseItemView::toInventoryItem)
                .collect(Collectors.toList());
    }
}
