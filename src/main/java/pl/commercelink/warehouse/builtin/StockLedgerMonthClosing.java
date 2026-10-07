package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closing a month stores its stock ledger report, and later reports start from its closing balance instead of the
 * whole history. Invoices stay editable after the closing: syncing one rewrites the prices of the warehouse documents
 * of its delivery, so a corrected month is generated again, together with the closed months after it.
 */
@Service
public class StockLedgerMonthClosing {

    private final StockLedgerService stockLedgerService;
    private final StockLedgerClosings closings;
    private final WarehouseDocumentRepository documents;
    private final DeliveriesRepository deliveries;
    // the zone of LocalDateTime.now(), which stamps the warehouse documents
    private final Clock clock;

    @Autowired
    StockLedgerMonthClosing(StockLedgerService stockLedgerService, StockLedgerClosings closings,
                            WarehouseDocumentRepository documents, DeliveriesRepository deliveries) {
        this(stockLedgerService, closings, documents, deliveries, Clock.systemDefaultZone());
    }

    StockLedgerMonthClosing(StockLedgerService stockLedgerService, StockLedgerClosings closings,
                            WarehouseDocumentRepository documents, DeliveriesRepository deliveries, Clock clock) {
        this.stockLedgerService = stockLedgerService;
        this.closings = closings;
        this.documents = documents;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    public StockLedgerClosingStatus status(String storeId) {
        List<YearMonth> closed = closings.closedMonths(storeId);
        YearMonth next = closed.isEmpty()
                ? YearMonth.now(clock).minusMonths(1)
                : closed.get(closed.size() - 1).plusMonths(1);
        return new StockLedgerClosingStatus(closed, next, next.atEndOfMonth().isBefore(LocalDate.now(clock)));
    }

    public StockLedgerClosingResult close(String storeId, YearMonth month) throws IOException {
        StockLedgerClosingStatus status = status(storeId);
        if (!month.equals(status.nextToClose())) {
            return new NotAllowed("reports.stockLedger.closing.error.notNext");
        }
        if (!status.nextClosable()) {
            return new NotAllowed("reports.stockLedger.closing.error.notOver");
        }
        return store(storeId, List.of(month), status.closedMonths().isEmpty());
    }

    /** Every closed month after the regenerated one starts from its closing balance, so they are generated again too. */
    public StockLedgerClosingResult regenerate(String storeId, YearMonth month) throws IOException {
        List<YearMonth> closed = closings.closedMonths(storeId);
        if (!closed.contains(month)) {
            return new NotAllowed("reports.stockLedger.closing.error.notClosed");
        }
        List<YearMonth> fromMonth = closed.stream().filter(closedMonth -> !closedMonth.isBefore(month)).toList();
        return store(storeId, fromMonth, month.equals(closed.get(0)));
    }

    public Optional<byte[]> closedReport(String storeId, YearMonth month) {
        return closings.find(storeId, month);
    }

    private StockLedgerClosingResult store(String storeId, List<YearMonth> months, boolean firstClosing) throws IOException {
        List<Delivery> unsettled = unsettledDeliveries(storeId, months.get(0), months.get(months.size() - 1), firstClosing);
        if (!unsettled.isEmpty()) {
            return new Blocked(unsettled);
        }
        // oldest first: each month starts from the closing balance of the one before it
        for (YearMonth month : months) {
            byte[] report = StockLedgerRow.toCsv(stockLedgerService.generate(storeId, month.atDay(1), month.atEndOfMonth()));
            closings.save(storeId, month, report);
        }
        return new Closed(months);
    }

    private List<Delivery> unsettledDeliveries(String storeId, YearMonth from, YearMonth to, boolean firstClosing) {
        // the first closed month holds the whole history in its opening balance, so every earlier delivery has to be settled too
        List<WarehouseDocument> received = firstClosing
                ? documents.findAllBeforeDate(storeId, to.plusMonths(1).atDay(1).atStartOfDay())
                : documents.findAllInDateRange(storeId, from.atDay(1).atStartOfDay(), to.atEndOfMonth().atTime(LocalTime.MAX));
        Set<String> deliveryIds = received.stream()
                .filter(document -> document.getType() == DocumentType.GoodsReceipt && document.getDeliveryId() != null)
                .map(WarehouseDocument::getDeliveryId)
                .collect(Collectors.toSet());
        return deliveries.findAllByIds(storeId, deliveryIds).stream()
                .filter(Delivery::isMissingInvoiceSync)
                .sorted(Comparator.comparing(Delivery::getReceivedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
