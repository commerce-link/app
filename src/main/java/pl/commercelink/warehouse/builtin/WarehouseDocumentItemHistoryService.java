package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
class WarehouseDocumentItemHistoryService {

    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;
    private final WarehouseDocumentRepository warehouseDocumentRepository;

    WarehouseDocumentItemHistoryService(
            WarehouseDocumentItemRepository warehouseDocumentItemRepository,
            WarehouseDocumentRepository warehouseDocumentRepository
    ) {
        this.warehouseDocumentItemRepository = warehouseDocumentItemRepository;
        this.warehouseDocumentRepository = warehouseDocumentRepository;
    }

    /** Every document of one delivery that moved this product, oldest first, with the stock after each move. Items
     *  whose document is not in the store are skipped: items are read by delivery id alone, which is not store-scoped. */
    EanHistory history(String storeId, String deliveryId, String ean) {
        List<WarehouseDocumentItem> items = warehouseDocumentItemRepository.findByDeliveryId(deliveryId).stream()
                .filter(item -> ean.equals(item.getEan()))
                .sorted(Comparator.comparing(WarehouseDocumentItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<EanHistoryRow> rows = new ArrayList<>();
        String productName = null;
        int runningStock = 0;
        for (WarehouseDocumentItem item : items) {
            WarehouseDocument document = warehouseDocumentRepository.findByDocumentId(storeId, item.getDocumentId());
            if (document == null) {
                continue;
            }
            int stockChange = stockChange(item);
            runningStock += stockChange;
            if (productName == null) {
                productName = item.getName();
            }
            rows.add(new EanHistoryRow(item.getDocumentId(), document.getDocumentNo(), item.getDocumentType(),
                    item.getCreatedAt(), item.getQty(), stockChange, runningStock, document.getReason(), document.getNote()));
        }
        return new EanHistory(productName, rows);
    }

    // legacy items may lack a type; their direction is unknown, so they do not move the running stock
    private static int stockChange(WarehouseDocumentItem item) {
        if (item.getDocumentType() == null) {
            return 0;
        }
        return item.getDocumentType().isReceiptType() ? item.getQty() : -item.getQty();
    }
}
