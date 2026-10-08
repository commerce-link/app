package pl.commercelink.web.reports;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** A closing held by deliveries without a settled invoice, carried as a flash attribute so it can be closed anyway. */
public record StockLedgerClosingWarning(LocalDate dateFrom, LocalDate dateTo, List<StockLedgerClosingBlocker> deliveries)
        implements Serializable {
}
