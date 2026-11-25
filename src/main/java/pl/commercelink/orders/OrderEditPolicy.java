package pl.commercelink.orders;

import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

/**
 * Which documents of an order still fix its data. By default every document does; a store can let its orders be
 * changed after a group of documents (Settings › Order fulfilment), so that nobody has to unpin a document, and lose
 * its id, only to correct the order. The document itself is never changed by an edit: bringing it in line with the
 * order (a correcting invoice, a corrected WZ) stays the store's job.
 *
 * <p>The groups: warehouse documents (WZ, reservation), sale documents (VAT, final and personal invoice, receipt) and
 * the documents before the sale (advance invoice, order, proforma). An e-receipt that is still being issued locks the
 * order whatever the store allows (ReceiptLock): its request is frozen and has no document yet. A closed order stays
 * read-only as well.
 *
 * <p>Only a store admin edits past a document: for anyone else the policy is {@link #STRICT} whatever the store set.
 * The same admin also edits an item past the New status, every field as on a new item, once the store released a group
 * and no document left on the order still locks ({@link #editsItemsInAnyStatus}); what that does to the item's delivery or the
 * stock is the store's own risk (owner decision 2026-11-24).
 */
public record OrderEditPolicy(boolean editAfterWarehouseDocuments, boolean editAfterSaleDocuments,
                              boolean editAfterAdvanceDocuments) {

    /** Every document locks: a store without the settings, and the callers that know no store. */
    public static final OrderEditPolicy STRICT = new OrderEditPolicy(false, false, false);

    public static OrderEditPolicy of(Store store) {
        FulfilmentConfiguration configuration = store == null ? null : store.getFulfilmentConfiguration();
        if (configuration == null) {
            return STRICT;
        }
        return new OrderEditPolicy(configuration.isOrderEditAfterWarehouseDocuments(),
                configuration.isOrderEditAfterSaleDocuments(), configuration.isOrderEditAfterAdvanceDocuments());
    }

    /** admin: the viewer is the store's ADMIN; the settings apply to nobody else. */
    public static OrderEditPolicy of(Store store, boolean admin) {
        return admin ? of(store) : STRICT;
    }

    /** Whether an item past the New status takes every field of its page, as a new one does. */
    public boolean editsItemsInAnyStatus(Order order) {
        return (editAfterWarehouseDocuments || editAfterSaleDocuments || editAfterAdvanceDocuments) && !priceLocks(order);
    }

    /** The order's invoice or receipt fixes what it lists: items, their names, quantities and VAT rates. */
    public boolean saleDocumentLocks(Order order) {
        return !editAfterSaleDocuments && order.hasClosingDocument();
    }

    /** As {@link Order#isInvoiced()}: an RMA replacement order counts as invoiced whatever the store allows. */
    public boolean invoiceLocks(Order order) {
        return order.isRMAReplacementOrder() || saleDocumentLocks(order);
    }

    public boolean goodsIssueLocks(Order order) {
        return !editAfterWarehouseDocuments && order.getDocumentByType(DocumentType.GoodsIssue).isPresent();
    }

    /** Item prices are on every document of the order, so any document the store did not release fixes them. */
    public boolean priceLocks(Order order) {
        return order.getDocuments().stream().anyMatch(this::locks);
    }

    private boolean locks(Document document) {
        DocumentType type = document.getType();
        if (type == null) {
            return true;
        }
        if (type.isWarehouseDocument()) {
            return !editAfterWarehouseDocuments;
        }
        return type.isClosingInvoice() ? !editAfterSaleDocuments : !editAfterAdvanceDocuments;
    }
}
