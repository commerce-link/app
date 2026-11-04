package pl.commercelink.inventory;

import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

class WarehouseInventorySource implements InventorySource {

    private final String storeId;
    private final StockQueryService stockQueryService;

    WarehouseInventorySource(String storeId, StockQueryService stockQueryService) {
        this.storeId = storeId;
        this.stockQueryService = stockQueryService;
    }

    @Override
    public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
        findAvailableByEanThenManufacturerCode(lookupKey).stream()
                .map(WarehouseItemView::toInventoryItem)
                .forEach(result::addAlternativeInventoryItem);
    }

    // TODO remove search by manufacturer sku when all products have EAN codes
    private List<WarehouseItemView> findAvailableByEanThenManufacturerCode(InventoryKey lookupKey) {
        List<WarehouseItemView> byEan = stockQueryService.searchAvailableByEans(storeId, lookupKey.getProductEans());
        return byEan.isEmpty() ? stockQueryService.searchAvailableByMfns(storeId, lookupKey.getProductCodes()) : byEan;
    }
}
