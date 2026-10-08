package pl.commercelink.warehouse.builtin;

import pl.commercelink.inventory.deliveries.Delivery;

import java.util.List;

public sealed interface StockLedgerClosingResult {

    /** A regenerated period whose closing balance changed leaves the closed periods after it out of date. */
    record Closed(StockLedgerPeriod period, boolean regenerated, List<StockLedgerPeriod> outdatedLaterPeriods)
            implements StockLedgerClosingResult {
    }

    /** Deliveries received into the warehouse in the period that still wait for their purchase invoice or its sync. */
    record Blocked(List<Delivery> deliveries) implements StockLedgerClosingResult {
    }

    /** The message arguments follow the period's label, which is always the first one. */
    record NotAllowed(String messageKey, List<Object> arguments) implements StockLedgerClosingResult {

        public NotAllowed(String messageKey) {
            this(messageKey, List.of());
        }
    }
}
