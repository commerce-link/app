package pl.commercelink.warehouse.api;

public record StockSummary(int products, int inStockQty, int inDeliveryQty) {

    public static final StockSummary EMPTY = new StockSummary(0, 0, 0);
}
