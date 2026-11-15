package pl.commercelink.inventory;

import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The store's sealed warehouse stock as one more source of offers. The stock is read once, on the first lookup, and kept
 * for the life of the view: a view assembles every product of a category (a price list, an automatic category's list),
 * and asking the warehouse about each of them was one read of the table per product, thousands for one page.
 */
class WarehouseInventorySource implements InventorySource {

    private final String storeId;
    private final StockQueryService stockQueryService;
    private Map<String, List<WarehouseItemView>> sealedStockByEan;

    WarehouseInventorySource(String storeId, StockQueryService stockQueryService) {
        this.storeId = storeId;
        this.stockQueryService = stockQueryService;
    }

    @Override
    public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
        Map<String, List<WarehouseItemView>> stock = sealedStockByEan();
        lookupKey.getProductEans().stream()
                .flatMap(ean -> stock.getOrDefault(ean, List.of()).stream())
                .map(WarehouseItemView::toInventoryItem)
                .forEach(result::addAlternativeInventoryItem);
    }

    private synchronized Map<String, List<WarehouseItemView>> sealedStockByEan() {
        if (sealedStockByEan == null) {
            sealedStockByEan = stockQueryService.searchAllAvailable(storeId).stream()
                    .filter(WarehouseItemView::isSealed)
                    .filter(item -> item.getEan() != null)
                    .collect(Collectors.groupingBy(WarehouseItemView::getEan));
        }
        return sealedStockByEan;
    }
}
