package pl.commercelink.warehouse.builtin;

import java.time.LocalDate;
import java.util.Comparator;

/** A closed stock ledger period, both days included; usually a calendar month, but any range can be closed. */
public record StockLedgerPeriod(LocalDate from, LocalDate to) {

    public static final Comparator<StockLedgerPeriod> BY_END = Comparator
            .comparing(StockLedgerPeriod::to)
            .thenComparing(StockLedgerPeriod::from);

    public String label() {
        return from + " – " + to;
    }
}
