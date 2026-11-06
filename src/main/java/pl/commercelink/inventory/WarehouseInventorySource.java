package pl.commercelink.inventory;

import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

class WarehouseInventorySource implements InventorySource {

    private final String storeId;
    private final StockQueryService stockQueryService;

    WarehouseInventorySource(String storeId, StockQueryService stockQueryService) {
        this.storeId = storeId;
        this.stockQueryService = stockQueryService;
    }

    @Override
    public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
        stockQueryService.searchAvailableByEans(storeId, lookupKey.getProductEans()).stream()
                .map(WarehouseItemView::toInventoryItem)
                .forEach(result::addAlternativeInventoryItem);
    }
}
