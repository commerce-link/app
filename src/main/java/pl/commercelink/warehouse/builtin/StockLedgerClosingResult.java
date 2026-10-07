package pl.commercelink.warehouse.builtin;

import pl.commercelink.inventory.deliveries.Delivery;

import java.time.YearMonth;
import java.util.List;

public sealed interface StockLedgerClosingResult {

    /** The months whose report was stored, oldest first. */
    record Closed(List<YearMonth> months) implements StockLedgerClosingResult {
    }

    /** Deliveries received into the warehouse that still wait for their purchase invoice or its sync. */
    record Blocked(List<Delivery> deliveries) implements StockLedgerClosingResult {
    }

    record NotAllowed(String messageKey) implements StockLedgerClosingResult {
    }
}
