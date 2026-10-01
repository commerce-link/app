package pl.commercelink.inventory.deliveries;

import java.util.List;

/** What "Pobierz wpłaty z systemu fakturowego" did, for the message on the Payments page (spec §6.2). */
public record InvoiceSyncResult(boolean configured, int checked, List<String> paidDeliveries, int unpaid,
                                List<String> failedInvoices) {

    public static InvoiceSyncResult notConfigured() {
        return new InvoiceSyncResult(false, 0, List.of(), 0, List.of());
    }
}
