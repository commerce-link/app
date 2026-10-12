package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * The closing button of the reports page. It names the first period still to close once that period has ended; a store
 * that never closed a period gets no reminder, as it may not use the closings at all.
 */
public record StockLedgerClosingShortcut(boolean pending, String month, String lastClosed) {

    public static StockLedgerClosingShortcut of(List<StockLedgerPeriod> closed, LocalDate today, Locale locale) {
        return closed.stream().max(StockLedgerPeriod.BY_END)
                .map(last -> {
                    StockLedgerPeriod next = last.next();
                    boolean pending = next.to().isBefore(today);
                    return new StockLedgerClosingShortcut(pending,
                            pending ? next.from().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) : null,
                            StockLedgerPeriodLabel.of(last, locale));
                })
                .orElse(new StockLedgerClosingShortcut(false, null, null));
    }
}
