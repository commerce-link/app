package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The stock ledger closing page: the range offered to close and the closed periods, newest first. Once a period is
 * closed the next one must start the day after it, so the start is fixed and only the end is chosen; the server still
 * checks the chain, so a hand-made request gets the same refusal as before.
 */
public record StockLedgerClosingView(LocalDate dateFrom, LocalDate dateTo, boolean fromFixed, String fromLabel,
                                     String previousLabel, List<ClosedPeriod> closedPeriods) {

    private static final String CLOSE_PATH = "/dashboard/reports/stock-ledger/close";
    /** Closed periods shown at once; a store closing every month would otherwise grow the page by a row a month. */
    public static final int RECENT_PERIODS = 6;

    public record ClosedPeriod(LocalDate from, LocalDate to, String label, String rangeText, String regenerateHref,
                               String generatedAt) {
    }

    public static StockLedgerClosingView of(List<StockLedgerPeriod> closed, LocalDate fallbackFrom, LocalDate fallbackTo, Locale locale) {
        return of(closed, Map.of(), fallbackFrom, fallbackTo, locale);
    }

    /** {@code generatedAt} holds the moment each closed period's report was last generated; a missing one shows a dash. */
    public static StockLedgerClosingView of(List<StockLedgerPeriod> closed, Map<StockLedgerPeriod, Instant> generatedAt,
                                            LocalDate fallbackFrom, LocalDate fallbackTo, Locale locale) {
        List<ClosedPeriod> periods = closed.stream()
                .sorted(StockLedgerPeriod.BY_END.reversed())
                .map(period -> new ClosedPeriod(period.from(), period.to(), StockLedgerPeriodLabel.of(period, locale),
                        new ReportPeriod(period.from(), period.to()).rangeText(),
                        CLOSE_PATH + "?dateFrom=" + period.from() + "&dateTo=" + period.to(),
                        generatedAt.containsKey(period) ? ReportTimes.format(generatedAt.get(period)) : null))
                .toList();
        StockLedgerPeriod last = closed.stream().max(StockLedgerPeriod.BY_END).orElse(null);
        StockLedgerPeriod offered = last != null ? last.next() : new StockLedgerPeriod(fallbackFrom, fallbackTo);
        return new StockLedgerClosingView(offered.from(), offered.to(), last != null, dayText(offered.from()),
                last != null ? StockLedgerPeriodLabel.of(last, locale) : null, periods);
    }

    /** The newest closed periods: the ones downloaded and generated again in practice. */
    public List<ClosedPeriod> recentPeriods() {
        return closedPeriods.subList(0, Math.min(RECENT_PERIODS, closedPeriods.size()));
    }

    /** The rest, behind "Older periods" on the page. */
    public List<ClosedPeriod> olderPeriods() {
        return closedPeriods.size() > RECENT_PERIODS ? closedPeriods.subList(RECENT_PERIODS, closedPeriods.size()) : List.of();
    }

    public StockLedgerClosingView withRange(LocalDate from, LocalDate to) {
        // a held regeneration starts after the closed period before it; the oldest one has none, so nothing is named
        String labelBefore = closedPeriods.stream()
                .filter(closed -> closed.to().plusDays(1).equals(from))
                .map(ClosedPeriod::label)
                .findFirst()
                .orElse(null);
        return new StockLedgerClosingView(from, to, fromFixed, dayText(from), labelBefore, closedPeriods);
    }

    private static String dayText(LocalDate day) {
        return day.getDayOfMonth() + "." + (day.getMonthValue() < 10 ? "0" : "") + day.getMonthValue() + "." + day.getYear();
    }
}
