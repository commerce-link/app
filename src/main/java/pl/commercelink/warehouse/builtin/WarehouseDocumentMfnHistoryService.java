package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
class WarehouseDocumentMfnHistoryService {

    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;
    private final WarehouseDocumentRepository warehouseDocumentRepository;

    WarehouseDocumentMfnHistoryService(
            WarehouseDocumentItemRepository warehouseDocumentItemRepository,
            WarehouseDocumentRepository warehouseDocumentRepository
    ) {
        this.warehouseDocumentItemRepository = warehouseDocumentItemRepository;
        this.warehouseDocumentRepository = warehouseDocumentRepository;
    }

    List<MfnHistoryRow> getMfnHistory(String storeId, String deliveryId, String mfn) {
        List<WarehouseDocumentItem> items = warehouseDocumentItemRepository.findByDeliveryId(deliveryId).stream()
                .filter(item -> mfn.equals(item.getMfn()))
                .sorted(Comparator.comparing(WarehouseDocumentItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<MfnHistoryRow> rows = new ArrayList<>();
        int runningStock = 0;
        for (WarehouseDocumentItem item : items) {
            int stockChange = item.getDocumentType().isReceiptType() ? item.getQty() : -item.getQty();
            runningStock += stockChange;
            WarehouseDocument document = warehouseDocumentRepository.findByDocumentId(storeId, item.getDocumentId());
            String documentNo = document != null ? document.getDocumentNo() : item.getDocumentId();
            rows.add(new MfnHistoryRow(item.getDocumentId(), documentNo, item.getDocumentType(), item.getCreatedAt(), item.getQty(), stockChange, runningStock));
        }
        return rows;
    }

    record MfnHistoryRow(String documentId, String documentNo, DocumentType documentType, LocalDateTime createdAt, int qty, int stockChange, int stockAfter) {}
}
