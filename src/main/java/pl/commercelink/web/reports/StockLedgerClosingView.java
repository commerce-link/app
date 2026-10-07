package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerClosingStatus;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The month closing section of the reports page: the next month to close and the closed ones, newest first. */
public record StockLedgerClosingView(String nextMonth, String nextLabel, boolean closable, String closableFrom,
                                     List<ClosedMonth> closedMonths) {

    public record ClosedMonth(String month, String label) {
    }

    public static StockLedgerClosingView of(StockLedgerClosingStatus status, Locale locale) {
        List<ClosedMonth> closed = status.closedMonths().stream()
                .sorted(Comparator.reverseOrder())
                .map(month -> new ClosedMonth(month.toString(), label(month, locale)))
                .toList();
        return new StockLedgerClosingView(status.nextToClose().toString(), label(status.nextToClose(), locale),
                status.nextClosable(), DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(status.closableFrom()), closed);
    }

    public static String label(YearMonth month, Locale locale) {
        return DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month);
    }
}
