package pl.commercelink.web.reports;

import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.format.TextStyle;
import java.util.Locale;

/** How a stock ledger period reads on screen: a full month by its name, any other range written out. */
public final class StockLedgerPeriodLabel {

    private StockLedgerPeriodLabel() {
    }

    public static String of(StockLedgerPeriod period, Locale locale) {
        return new ReportPeriod(period.from(), period.to()).isFullMonth()
                ? period.from().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + period.from().getYear()
                : new ReportPeriod(period.from(), period.to()).rangeText();
    }
}
