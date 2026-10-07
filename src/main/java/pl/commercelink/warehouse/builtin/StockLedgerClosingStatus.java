package pl.commercelink.warehouse.builtin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Months are closed one after another, so the only month that can be closed is the one after the last closed. */
public record StockLedgerClosingStatus(List<YearMonth> closedMonths, YearMonth nextToClose, boolean nextClosable) {

    public LocalDate closableFrom() {
        return nextToClose.plusMonths(1).atDay(1);
    }
}
