package pl.commercelink.inventory.deliveries;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What "Połącz i zsynchronizuj faktury" did. The deliveries are named by their short ids; {@code remaining} is how
 * many the run had no time for, and {@code continueAfter} the reception time the next run starts after.
 */
public record InvoiceAutoSyncResult(boolean configured, List<String> synced, int noInvoice, int severalInvoices,
                                    int needsReview, List<String> failed, int remaining, LocalDateTime continueAfter) {

    public static InvoiceAutoSyncResult notConfigured() {
        return new InvoiceAutoSyncResult(false, List.of(), 0, 0, 0, List.of(), 0, null);
    }

    public int unchanged() {
        return noInvoice + severalInvoices + needsReview;
    }

    public int checked() {
        return synced.size() + unchanged() + failed.size();
    }
}
