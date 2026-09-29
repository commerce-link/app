package pl.commercelink.receipts;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the order page needs to know about the order's e-receipt, derived from one read of its attempts
 * ({@link ReceiptAttemptService#orderState}), so a page render queries the attempts store once.
 *
 * @param attempts            every attempt of the order, in no particular order
 * @param view                the rows of the order's e-receipt, newest attempt first
 * @param canIssueManually    "E-paragon" may be issued from the order's documents ({@link ReceiptAttemptService#canIssueManually})
 * @param blocksManualReceipt an attempt owns the order's receipt ({@link ReceiptAttemptService#blocksManualReceipt})
 */
public record ReceiptOrderState(List<ReceiptAttempt> attempts, ReceiptOrderView view, boolean canIssueManually,
                                boolean blocksManualReceipt) {

    public static final ReceiptOrderState NONE =
            new ReceiptOrderState(List.of(), new ReceiptOrderView(List.of(), false), false, false);

    public ReceiptOrderState {
        attempts = List.copyOf(attempts);
    }

    /**
     * Whether edits that an issued invoice locks must be locked for the receipt too: an attempt owns the order's
     * receipt (its request snapshot is frozen) but the receipt document is not on the order yet. Once the document is
     * attached the order is invoiced ({@code DocumentType.Receipt} is a closing document) and the invoiced locks apply
     * by themselves. Wider than "ISSUING or PENDING" on purpose: a FISCALISED attempt whose document is still being
     * attached, or a CLOSED_MANUALLY one whose order write is being retried, has a registered sale that the order must
     * not drift from either.
     */
    public boolean locksOrder(Order order) {
        return locksOrder(blocksManualReceipt, order);
    }

    /** The same rule for a caller that only asked {@link ReceiptAttemptService#blocksManualReceipt}. */
    public static boolean locksOrder(boolean blocksManualReceipt, Order order) {
        return blocksManualReceipt && !order.isInvoiced();
    }

    /**
     * Whether the order's e-receipt is registered in fiscal memory or was closed by hand with a document resolved at
     * the provider: an attempt is FISCALISED or CLOSED_MANUALLY, or its document is on the order. Cancelling the order
     * does not undo such a receipt, so the cancel confirmation says so.
     */
    public boolean hasFiscalisedReceipt(Order order) {
        return hasFiscalisedReceipt(attempts, order);
    }

    /** The same rule over attempts the caller already read. */
    public static boolean hasFiscalisedReceipt(List<ReceiptAttempt> orderAttempts, Order order) {
        boolean registered = orderAttempts.stream().anyMatch(a -> a.getState() == ReceiptAttemptState.FISCALISED
                || a.getState() == ReceiptAttemptState.CLOSED_MANUALLY);
        return registered || order.getDocuments().stream()
                .filter(d -> d.getType() == DocumentType.Receipt)
                .anyMatch(d -> orderAttempts.stream().anyMatch(a -> Objects.equals(a.getReceiptKey(), d.getId())));
    }

    /** The attempt that wrote the order document with this id (an e-receipt's document id is its attempt's key). */
    public Optional<ReceiptAttempt> attemptOfDocument(String documentId) {
        return attempts.stream().filter(a -> Objects.equals(a.getReceiptKey(), documentId)).findFirst();
    }
}
