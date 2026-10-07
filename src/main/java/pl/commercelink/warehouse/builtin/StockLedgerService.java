package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.builtin.StockLedgerClosings.ClosingBalance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StockLedgerService {

    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;
    private final StockLedgerClosings closings;

    public List<StockLedgerRow> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime periodStart = dateFrom.atStartOfDay();
        LocalDateTime periodEnd = dateTo.atTime(LocalTime.MAX);

        Map<String, Aggregate> aggregates = openingBalance(storeId, periodStart);

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

    private Map<String, Aggregate> openingBalance(String storeId, LocalDateTime periodStart) {
        Map<String, Aggregate> aggregates = new HashMap<>();
        Optional<Closing> closing = lastClosingBefore(storeId, periodStart.toLocalDate());
        if (closing.isEmpty()) {
            accumulate(warehouseDocumentRepository.findAllBeforeDate(storeId, periodStart), aggregates, Bucket.OPENING);
            return aggregates;
        }

        closing.get().balances().forEach((mfn, balance) -> aggregates.put(mfn, Aggregate.opening(balance)));
        LocalDateTime afterClosedMonth = closing.get().month().plusMonths(1).atDay(1).atStartOfDay();
        if (afterClosedMonth.isBefore(periodStart)) {
            List<WarehouseDocument> sinceClosing =
                    warehouseDocumentRepository.findAllInDateRange(storeId, afterClosedMonth, periodStart.minusNanos(1));
            accumulate(sinceClosing, aggregates, Bucket.OPENING);
        }
        return aggregates;
    }

    // a closed month that cannot be read leaves the opening balance to the whole history: slower, but still right
    private Optional<Closing> lastClosingBefore(String storeId, LocalDate dateFrom) {
        try {
            return closings.closedMonths(storeId).stream()
                    .filter(month -> month.atEndOfMonth().isBefore(dateFrom))
                    .max(Comparator.naturalOrder())
                    .map(month -> new Closing(month, closings.closingBalances(storeId, month)));
        } catch (RuntimeException e) {
            log.error("Closed stock ledger of store {} could not be read, the opening balance is taken from the whole history", storeId, e);
            return Optional.empty();
        }
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

    private record Closing(YearMonth month, Map<String, ClosingBalance> balances) {
    }

    private static class Aggregate {
        String latestName;
        int boQty;
        double boValue;
        final Map<LedgerCategory, Integer> qty = new EnumMap<>(LedgerCategory.class);
        final Map<LedgerCategory, Double> value = new EnumMap<>(LedgerCategory.class);

        static Aggregate opening(ClosingBalance balance) {
            Aggregate aggregate = new Aggregate();
            aggregate.latestName = balance.name();
            aggregate.boQty = balance.qty();
            aggregate.boValue = balance.value();
            return aggregate;
        }

        void add(LedgerCategory category, int q, double v) {
            qty.merge(category, q, Integer::sum);
            value.merge(category, v, Double::sum);
        }
    }
}
