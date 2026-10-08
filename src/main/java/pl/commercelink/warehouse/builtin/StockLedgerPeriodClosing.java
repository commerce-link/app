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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closing a period stores its stock ledger report, and reports starting after it take its closing balance instead of
 * reading the whole history. Any past range can be closed, in any order, so months can be closed back in time. Closing
 * a closed range again regenerates it, e.g. after an invoice correction rewrote the prices of its documents; the closed
 * periods after it start from its closing balance, and regenerating them is left to the user.
 */
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

    /** Deliveries without a settled invoice only warn: the operator may close the period anyway. */
    public StockLedgerClosingResult close(String storeId, StockLedgerPeriod period, boolean despiteUnsettledDeliveries)
            throws IOException {
        if (period.from().isAfter(period.to())) {
            return new NotAllowed("reports.stockLedger.closing.error.range");
        }
        if (!period.to().isBefore(LocalDate.now(clock))) {
            return new NotAllowed("reports.stockLedger.closing.error.notOver");
        }
        if (!despiteUnsettledDeliveries) {
            List<Delivery> unsettled = unsettledDeliveries(storeId, period);
            if (!unsettled.isEmpty()) {
                return new Blocked(unsettled);
            }
        }

        boolean closedBefore = closings.exists(storeId, period);
        byte[] report = StockLedgerRow.toCsv(stockLedgerService.generate(storeId, period.from(), period.to()));
        closings.save(storeId, period, report);
        return new Closed(period, closedBefore);
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
