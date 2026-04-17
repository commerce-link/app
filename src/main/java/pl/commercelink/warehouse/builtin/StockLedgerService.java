package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockLedgerService {

    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    StockLedgerService(
            WarehouseDocumentRepository warehouseDocumentRepository,
            WarehouseDocumentItemRepository warehouseDocumentItemRepository
    ) {
        this.warehouseDocumentRepository = warehouseDocumentRepository;
        this.warehouseDocumentItemRepository = warehouseDocumentItemRepository;
    }

    public List<StockLedgerRow> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime periodStart = dateFrom.atStartOfDay();
        LocalDateTime periodEnd = dateTo.atTime(LocalTime.MAX);

        Map<String, Aggregate> aggregates = new HashMap<>();

        List<WarehouseDocument> historical = warehouseDocumentRepository.findAllBeforeDate(storeId, periodStart);
        accumulate(historical, aggregates, Bucket.OPENING);

        List<WarehouseDocument> inPeriod = warehouseDocumentRepository.findAllInDateRange(storeId, periodStart, periodEnd);
        accumulate(inPeriod, aggregates, Bucket.PERIOD);

        List<StockLedgerRow> rows = new ArrayList<>();
        for (Map.Entry<String, Aggregate> entry : aggregates.entrySet()) {
            Aggregate a = entry.getValue();

            int bzQty = a.boQty + a.inQty - a.outQty;
            double bzValue = a.boValue + a.inValue - a.outValue;

            boolean emptyRow = a.boQty == 0 && a.inQty == 0 && a.outQty == 0
                    && a.boValue == 0.0 && a.inValue == 0.0 && a.outValue == 0.0;
            if (emptyRow) continue;

            rows.add(new StockLedgerRow(
                    entry.getKey(),
                    a.latestName,
                    a.boQty, round(a.boValue),
                    a.inQty, round(a.inValue),
                    a.outQty, round(a.outValue),
                    bzQty, round(bzValue)
            ));
        }

        rows.sort(Comparator.comparing(StockLedgerRow::mfn, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private void accumulate(List<WarehouseDocument> documents, Map<String, Aggregate> aggregates, Bucket bucket) {
        documents.sort(Comparator.comparing(WarehouseDocument::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        for (WarehouseDocument document : documents) {
            DocumentType type = document.getType();
            if (type == null || !isStockMovement(type)) continue;

            List<WarehouseDocumentItem> items = warehouseDocumentItemRepository.findByDocumentNo(document.getDocumentNo());
            for (WarehouseDocumentItem item : items) {
                if (item.getMfn() == null || item.getMfn().isBlank()) continue;

                Aggregate a = aggregates.computeIfAbsent(item.getMfn(), k -> new Aggregate());
                if (item.getName() != null && !item.getName().isBlank()) {
                    a.latestName = item.getName();
                }

                int qty = item.getQty();
                double value = qty * item.getUnitPrice();
                boolean isReceipt = type.isReceiptType();

                if (bucket == Bucket.OPENING) {
                    if (isReceipt) {
                        a.boQty += qty;
                        a.boValue += value;
                    } else {
                        a.boQty -= qty;
                        a.boValue -= value;
                    }
                } else {
                    if (isReceipt) {
                        a.inQty += qty;
                        a.inValue += value;
                    } else {
                        a.outQty += qty;
                        a.outValue += value;
                    }
                }
            }
        }
    }

    private boolean isStockMovement(DocumentType type) {
        return type == DocumentType.GoodsReceipt
                || type == DocumentType.GoodsIssue
                || type == DocumentType.InternalReceipt
                || type == DocumentType.InternalIssue;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private enum Bucket { OPENING, PERIOD }

    private static class Aggregate {
        String latestName;
        int boQty;
        double boValue;
        int inQty;
        double inValue;
        int outQty;
        double outValue;
    }
}
