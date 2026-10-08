package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.List;

/** The period closing section of the reports page: the range to close and the closed periods, newest first. */
public record StockLedgerClosingView(LocalDate dateFrom, LocalDate dateTo, List<ClosedPeriod> closedPeriods) {

    public record ClosedPeriod(LocalDate from, LocalDate to, String label) {
    }

    public static StockLedgerClosingView of(List<StockLedgerPeriod> closed, LocalDate dateFrom, LocalDate dateTo) {
        List<ClosedPeriod> periods = closed.stream()
                .sorted(StockLedgerPeriod.BY_END.reversed())
                .map(period -> new ClosedPeriod(period.from(), period.to(), period.label()))
                .toList();
        return new StockLedgerClosingView(dateFrom, dateTo, periods);
    }
}
