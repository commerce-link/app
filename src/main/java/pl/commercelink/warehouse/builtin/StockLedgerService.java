package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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

            boolean emptyRow = a.boQty == 0 && a.boValue == 0.0 && a.qty.isEmpty();
            if (emptyRow) continue;

            Map<LedgerCategory, Double> roundedValue = new EnumMap<>(LedgerCategory.class);
            a.value.forEach((category, value) -> roundedValue.put(category, round(value)));

            rows.add(new StockLedgerRow(
                    entry.getKey(),
                    a.latestName,
                    a.boQty, round(a.boValue),
                    a.qty,
                    roundedValue
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

            List<WarehouseDocumentItem> items = warehouseDocumentItemRepository.findByDocumentId(document.getDocumentId());
            for (WarehouseDocumentItem item : items) {
                if (item.getMfn() == null || item.getMfn().isBlank()) continue;

                Aggregate a = aggregates.computeIfAbsent(item.getMfn(), k -> new Aggregate());
                if (item.getName() != null && !item.getName().isBlank()) {
                    a.latestName = item.getName();
                }

                int qty = item.getQty();
                double value = qty * item.getUnitPrice();

                if (bucket == Bucket.OPENING) {
                    if (type.isReceiptType()) {
                        a.boQty += qty;
                        a.boValue += value;
                    } else {
                        a.boQty -= qty;
                        a.boValue -= value;
                    }
                } else {
                    LedgerCategory category = LedgerCategory.resolve(type, document.getReason());
                    a.add(category, qty, value);
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
        final Map<LedgerCategory, Integer> qty = new EnumMap<>(LedgerCategory.class);
        final Map<LedgerCategory, Double> value = new EnumMap<>(LedgerCategory.class);

        void add(LedgerCategory category, int q, double v) {
            qty.merge(category, q, Integer::sum);
            value.merge(category, v, Double::sum);
        }
    }
}
