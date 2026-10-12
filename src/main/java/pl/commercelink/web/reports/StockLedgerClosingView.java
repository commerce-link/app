package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The stock ledger closing page: the range offered to close and the closed periods, newest first. Once a period is
 * closed the next one must start the day after it, so the start is fixed and only the end is chosen; the server still
 * checks the chain, so a hand-made request gets the same refusal as before.
 */
public record StockLedgerClosingView(LocalDate dateFrom, LocalDate dateTo, boolean fromFixed, String fromLabel,
                                     String previousLabel, List<ClosedPeriod> closedPeriods) {

    private static final String CLOSE_PATH = "/dashboard/reports/stock-ledger/close";

    public record ClosedPeriod(LocalDate from, LocalDate to, String label, String rangeText, String regenerateHref) {
    }

    public static StockLedgerClosingView of(List<StockLedgerPeriod> closed, LocalDate fallbackFrom, LocalDate fallbackTo, Locale locale) {
        List<ClosedPeriod> periods = closed.stream()
                .sorted(StockLedgerPeriod.BY_END.reversed())
                .map(period -> new ClosedPeriod(period.from(), period.to(), StockLedgerPeriodLabel.of(period, locale),
                        new ReportPeriod(period.from(), period.to()).rangeText(),
                        CLOSE_PATH + "?dateFrom=" + period.from() + "&dateTo=" + period.to()))
                .toList();
        StockLedgerPeriod last = closed.stream().max(StockLedgerPeriod.BY_END).orElse(null);
        StockLedgerPeriod offered = last != null ? last.next() : new StockLedgerPeriod(fallbackFrom, fallbackTo);
        return new StockLedgerClosingView(offered.from(), offered.to(), last != null, dayText(offered.from()),
                last != null ? StockLedgerPeriodLabel.of(last, locale) : null, periods);
    }

    public StockLedgerClosingView withRange(LocalDate from, LocalDate to) {
        // a held regeneration of an older period starts after the one before it, not after the newest closed period
        String labelBefore = closedPeriods.stream()
                .filter(closed -> closed.to().plusDays(1).equals(from))
                .map(ClosedPeriod::label)
                .findFirst()
                .orElse(previousLabel);
        return new StockLedgerClosingView(from, to, fromFixed, dayText(from), labelBefore, closedPeriods);
    }

    private static String dayText(LocalDate day) {
        return day.getDayOfMonth() + "." + (day.getMonthValue() < 10 ? "0" : "") + day.getMonthValue() + "." + day.getYear();
    }
}
