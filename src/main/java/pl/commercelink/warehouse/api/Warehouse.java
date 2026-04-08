package pl.commercelink.warehouse.api;

public interface Warehouse {

    GoodsInHandler goodsInHandler(String storeId);
    GoodsOutHandler goodsOutHandler(String storeId);

    RmaGoodsInHandler rmaGoodsInHandler(String storeId);

    DocumentQueryService documentQueryService(String storeId);

    StockQueryService stockQueryService(String storeId);

    ReservationService reservationService(String storeId);

    InvoiceSyncHandler invoiceSyncHandler(String storeId);
}
