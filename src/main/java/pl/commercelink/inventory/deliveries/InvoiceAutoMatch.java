package pl.commercelink.inventory.deliveries;

import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.web.dtos.InvoiceSyncPreview;
import pl.commercelink.web.dtos.InvoiceSyncPreview.MatchState;

/**
 * Whether the sync page would be saved exactly as it opens: every product on its own invoice line at the same price,
 * the delivery's extra costs too, and no invoice line left over. Anything the operator would have to look at or
 * change is not a perfect match (owner's rule, 2026-11-25).
 */
public final class InvoiceAutoMatch {

    private InvoiceAutoMatch() {
    }

    public static boolean isPerfect(InvoiceSyncPreview preview) {
        return !preview.isDeliveryAwaitingApproval()
                && !preview.getMappings().isEmpty()
                && preview.getMappings().stream().allMatch(mapping -> preview.stateOf(mapping) == MatchState.EXACT)
                && matchesOrIsAbsent(preview.getShippingCostState())
                && matchesOrIsAbsent(preview.getPaymentCostState())
                && preview.getUnassignedOptions().isEmpty();
    }

    /**
     * The run changes no payment, so both sides have to agree already: paid in full on both, or no payment on either.
     * A part payment on one side is the operator's to settle.
     */
    public static boolean paymentsAgree(Invoice invoice, Delivery delivery) {
        return invoice.paid() ? delivery.isFullyPaid() : invoice.unpaid() && delivery.getPayments().isEmpty();
    }

    private static boolean matchesOrIsAbsent(MatchState extraCost) {
        return extraCost == MatchState.EXACT || extraCost == MatchState.NO_COST;
    }
}
