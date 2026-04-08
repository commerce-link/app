package pl.commercelink.warehouse.api;

import java.util.Collection;
import java.util.List;

public interface StockQueryService {

    List<WarehouseItemView> searchAvailableByMfns(String storeId, Collection<String> mfns);

    List<WarehouseItemView> searchByMfns(String storeId, Collection<String> mfns);

    WarehouseItemView findBySerialNo(String storeId, String serialNo);
}
