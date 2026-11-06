package pl.commercelink.warehouse.api;

import java.util.Collection;
import java.util.List;

public interface StockQueryService {

    List<WarehouseItemView> searchAvailableByEans(String storeId, Collection<String> eans);

    List<WarehouseItemView> searchByEans(String storeId, Collection<String> eans);

    /** Items of the store whose serial-number list holds exactly this number. */
    List<WarehouseItemView> findAllBySerialNo(String storeId, String serialNo);

    WarehouseItemView findById(String storeId, String itemId);

    List<WarehouseItemView> searchAllAvailableByEans(String storeId, Collection<String> eans);

    /** Every available item of the store (ordered or delivered, sealed or not), whatever its code. */
    List<WarehouseItemView> searchAllAvailable(String storeId);

    StockSummary summarizeAvailable(String storeId);
}
