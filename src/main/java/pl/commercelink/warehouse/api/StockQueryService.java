package pl.commercelink.warehouse.api;

import java.util.Collection;
import java.util.List;

public interface StockQueryService {

    List<WarehouseItemView> searchAvailableByMfns(String storeId, Collection<String> mfns);

    List<WarehouseItemView> searchNotSealedAvailableByMfns(String storeId, Collection<String> mfns);

    List<WarehouseItemView> searchByMfns(String storeId, Collection<String> mfns);

    /** Items of the store whose serial-number list holds exactly this number. */
    List<WarehouseItemView> findAllBySerialNo(String storeId, String serialNo);

    WarehouseItemView findById(String storeId, String itemId);

    List<WarehouseItemView> searchAllAvailableByMfns(String storeId, Collection<String> mfns);

    /** Every available item of the store (ordered or delivered, sealed or not), whatever its code. */
    List<WarehouseItemView> searchAllAvailable(String storeId);

    StockSummary summarizeAvailable(String storeId);
}
