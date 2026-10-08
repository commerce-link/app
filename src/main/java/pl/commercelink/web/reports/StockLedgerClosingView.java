package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.List;

/** The period closing section of the reports page: the range offered to close and the closed periods, newest first. */
public record StockLedgerClosingView(LocalDate dateFrom, LocalDate dateTo, List<ClosedPeriod> closedPeriods) {

    public record ClosedPeriod(LocalDate from, LocalDate to, String label) {
    }

    /** The range offered is the period after the last closed one; before any closing, the report's own range. */
    public static StockLedgerClosingView of(List<StockLedgerPeriod> closed, LocalDate reportFrom, LocalDate reportTo) {
        List<ClosedPeriod> periods = closed.stream()
                .sorted(StockLedgerPeriod.BY_END.reversed())
                .map(period -> new ClosedPeriod(period.from(), period.to(), period.label()))
                .toList();
        StockLedgerPeriod offered = closed.stream().max(StockLedgerPeriod.BY_END)
                .map(StockLedgerPeriod::next)
                .orElse(new StockLedgerPeriod(reportFrom, reportTo));
        return new StockLedgerClosingView(offered.from(), offered.to(), periods);
    }

    public StockLedgerClosingView withRange(LocalDate from, LocalDate to) {
        return new StockLedgerClosingView(from, to, closedPeriods);
    }
}
