package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;

import java.util.Objects;

/**
 * Why an item's sale fields (name, quantity, VAT rate) are fixed: the order's sale is registered and the order must not
 * drift from its document. INVOICED: a closing document is on the order ({@link Order#isInvoiced()}: an invoice or a
 * receipt); RECEIPT_ISSUING: an e-receipt is being issued and its request snapshot is frozen, but its document is not
 * on the order yet (ReceiptOrderState#locksOrder). A sale is corrected with a correcting invoice or a return, outside
 * the order page. Serial numbers, supplier, cost and comment are not sale fields and stay editable.
 *
 * <p>Each value carries the short reasons the item page shows next to the locked fields, the full refusal text of a
 * save that would change them, and the reasons of the items menu's "Split set" (which rewrites names, quantities and
 * prices of the lines).
 */
public enum ItemSaleLock {

    INVOICED("order.item.form.name.locked.invoiced", "order.item.form.numbers.locked.invoiced",
            "order.item.form.price.locked.invoiced", "order.item.error.sale.locked.invoiced",
            "order.item.unavailable.sale.invoiced", "order.item.split.group.locked.invoiced"),
    RECEIPT_ISSUING("order.item.form.name.locked.receipt", "order.item.form.numbers.locked.receipt",
            "order.item.form.price.locked.receipt", "order.item.error.sale.locked.receipt",
            "order.item.unavailable.receipt", "order.item.split.group.locked.receipt");

    private final String nameKey;
    private final String numbersKey;
    private final String priceKey;
    private final String refusalKey;
    private final String menuReasonKey;
    private final String splitGroupRefusalKey;

    ItemSaleLock(String nameKey, String numbersKey, String priceKey, String refusalKey, String menuReasonKey,
                 String splitGroupRefusalKey) {
        this.nameKey = nameKey;
        this.numbersKey = numbersKey;
        this.priceKey = priceKey;
        this.refusalKey = refusalKey;
        this.menuReasonKey = menuReasonKey;
        this.splitGroupRefusalKey = splitGroupRefusalKey;
    }

    /** The lock of this order, or null. receiptLocked: ReceiptOrderState#locksOrder (false once the order is invoiced). */
    public static ItemSaleLock of(Order order, boolean receiptLocked) {
        return order.isInvoiced() ? INVOICED : receiptLocked ? RECEIPT_ISSUING : null;
    }

    /**
     * Whether saving the posted item would change a sale field of the stored one, as {@link OrderItem#update} applies
     * them: the name always, quantity and VAT rate only for a new item (a fulfilled item's page does not post them and
     * the update ignores them). An unchanged value passes, so a form that posts every field still saves the others; a
     * name missing from the post would clear it, so it counts as a change.
     */
    public static boolean changes(OrderItem stored, OrderItem posted) {
        if (!Objects.equals(StringUtils.trimToNull(posted.getName()), StringUtils.trimToNull(stored.getName()))) {
            return true;
        }
        return stored.isNew() && (posted.getQty() != stored.getQty() || Math.abs(posted.getTax() - stored.getTax()) > 1e-9);
    }

    /**
     * Puts the stored sale fields back on the posted item before it is applied, so not even whitespace around the name
     * drifts from the document once {@link #changes} let the save through.
     */
    public static void keep(OrderItem stored, OrderItem posted) {
        posted.setName(stored.getName());
        posted.setQty(stored.getQty());
        posted.setTax(stored.getTax());
    }

    public String nameKey() { return nameKey; }
    public String numbersKey() { return numbersKey; }
    public String priceKey() { return priceKey; }
    public String refusalKey() { return refusalKey; }
    public String menuReasonKey() { return menuReasonKey; }
    public String splitGroupRefusalKey() { return splitGroupRefusalKey; }
}
