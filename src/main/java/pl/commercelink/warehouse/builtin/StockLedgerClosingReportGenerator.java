package pl.commercelink.warehouse.builtin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.reports.ReportGenerator;
import pl.commercelink.reports.ReportType;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Closes the period from the queue. The request was checked when it was ordered, but the world moves while it waits
 * (another period closed, a delivery changed), so the closing checks again; a refusal now is an expected skip that only
 * leaves the period open. Nothing tells the user about the later periods a regeneration made out of date, so it is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StockLedgerClosingReportGenerator implements ReportGenerator {

    private final StockLedgerPeriodClosing stockLedgerPeriodClosing;

    @Override
    public ReportType type() {
        return ReportType.STOCK_LEDGER_CLOSING;
    }

    @Override
    public void generate(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        StockLedgerClosingResult result = stockLedgerPeriodClosing.close(storeId, new StockLedgerPeriod(dateFrom, dateTo));
        if (result instanceof Closed closed && !closed.outdatedLaterPeriods().isEmpty()) {
            log.warn("Closing balance of {}..{} for store {} changed, later closed periods are out of date: {}",
                    dateFrom, dateTo, storeId, closed.outdatedLaterPeriods());
        } else if (!(result instanceof Closed)) {
            log.warn("Closing of {}..{} for store {} not done: {}", dateFrom, dateTo, storeId, result);
        }
    }
}
