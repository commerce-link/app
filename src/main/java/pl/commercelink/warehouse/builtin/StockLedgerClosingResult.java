package pl.commercelink.warehouse.builtin;

import pl.commercelink.inventory.deliveries.Delivery;

import java.util.List;

public sealed interface StockLedgerClosingResult {

    record Closed(StockLedgerPeriod period, boolean regenerated) implements StockLedgerClosingResult {
    }

    /** Deliveries received into the warehouse in the period that still wait for their purchase invoice or its sync. */
    record Blocked(List<Delivery> deliveries) implements StockLedgerClosingResult {
    }

    record NotAllowed(String messageKey) implements StockLedgerClosingResult {
    }
}
