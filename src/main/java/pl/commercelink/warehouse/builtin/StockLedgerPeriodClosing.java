package pl.commercelink.warehouse.builtin;

import lombok.extern.slf4j.Slf4j;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closing a period stores its stock ledger report, and reports starting after it take its closing balance instead of
 * reading the whole history. A period closes only when every delivery received into the warehouse in it has its
 * purchase invoice linked and synced. Closing a closed range again regenerates it, e.g. after an invoice correction
 * rewrote the prices of its documents; the closed periods after it start from its closing balance, and regenerating
 * them is left to the user.
 */
@Slf4j
@Service
public class StockLedgerPeriodClosing {

    private final StockLedgerService stockLedgerService;
    private final StockLedgerClosings closings;
    private final WarehouseDocumentRepository documents;
    private final DeliveriesRepository deliveries;
    // the zone of LocalDateTime.now(), which stamps the warehouse documents
    private final Clock clock;

    @Autowired
    StockLedgerPeriodClosing(StockLedgerService stockLedgerService, StockLedgerClosings closings,
                             WarehouseDocumentRepository documents, DeliveriesRepository deliveries) {
        this(stockLedgerService, closings, documents, deliveries, Clock.systemDefaultZone());
    }

    StockLedgerPeriodClosing(StockLedgerService stockLedgerService, StockLedgerClosings closings,
                             WarehouseDocumentRepository documents, DeliveriesRepository deliveries, Clock clock) {
        this.stockLedgerService = stockLedgerService;
        this.closings = closings;
        this.documents = documents;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    public List<StockLedgerPeriod> closedPeriods(String storeId) {
        return closings.closedPeriods(storeId);
    }

    /**
     * A new period starts the day after the last closed one, so the closed periods form one chain; only the first can
     * start anywhere, to close the history back in time. A closed range can always be closed again to regenerate it.
     */
    public StockLedgerClosingResult close(String storeId, StockLedgerPeriod period) throws IOException {
        if (period.from().isAfter(period.to())) {
            return new NotAllowed("reports.stockLedger.closing.error.range");
        }
        if (!period.to().isBefore(LocalDate.now(clock))) {
            return new NotAllowed("reports.stockLedger.closing.error.notOver");
        }
        List<StockLedgerPeriod> closed = closings.closedPeriods(storeId);
        boolean regenerating = closed.contains(period);
        if (!regenerating && !closed.isEmpty()) {
            LocalDate nextFrom = closed.get(closed.size() - 1).next().from();
            if (!period.from().equals(nextFrom)) {
                return new NotAllowed("reports.stockLedger.closing.error.gap", List.<Object>of(nextFrom));
            }
        }
        List<Delivery> unsettled = unsettledDeliveries(storeId, period);
        if (!unsettled.isEmpty()) {
            return new Blocked(unsettled);
        }

        byte[] report = StockLedgerRow.toCsv(stockLedgerService.generate(storeId, period.from(), period.to()));
        List<StockLedgerPeriod> outdated = regenerating ? outdatedAfter(storeId, period, closed, report) : List.of();
        closings.save(storeId, period, report);
        return new Closed(period, regenerating, outdated);
    }

    // the closed periods after a regenerated one start from its closing balance, so they are out of date once it changed
    private List<StockLedgerPeriod> outdatedAfter(String storeId, StockLedgerPeriod period, List<StockLedgerPeriod> closed,
                                                  byte[] report) {
        List<StockLedgerPeriod> later = closed.stream()
                .filter(closedPeriod -> closedPeriod.from().isAfter(period.to()))
                .toList();
        return later.isEmpty() || !closingBalanceChanged(storeId, period, report) ? List.of() : later;
    }

    private boolean closingBalanceChanged(String storeId, StockLedgerPeriod period, byte[] report) {
        try {
            return !closings.closingBalances(storeId, period).equals(StockLedgerClosings.parse(report));
        } catch (RuntimeException e) {
            log.warn("Previous stock ledger of {} for store {} could not be read, the later closed periods are reported as out of date",
                    period.label(), storeId, e);
            return true;
        }
    }

    public Optional<byte[]> closedReport(String storeId, StockLedgerPeriod period) {
        return closings.find(storeId, period);
    }

    private List<Delivery> unsettledDeliveries(String storeId, StockLedgerPeriod period) {
        List<WarehouseDocument> received = documents.findAllInDateRange(
                storeId, period.from().atStartOfDay(), period.to().atTime(LocalTime.MAX));
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
