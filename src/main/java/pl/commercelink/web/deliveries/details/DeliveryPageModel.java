package pl.commercelink.web.deliveries.details;

import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.web.orders.OrderLabels;

import java.util.List;

/**
 * Everything the delivery details page shows, worked out once by DeliveryPageModelFactory: message keys, tones,
 * formatted amounts and dates, links for the viewer and, for every action, whether it shows and why it is greyed. The
 * template only prints. openDialog: the dialog rendered open when the page answers a no-JavaScript opener.
 */
public record DeliveryPageModel(String deliveryId, String shortId, boolean superAdmin, boolean dropship,
                                String backHref, String backLabelKey, DeliveryLinks links, Header header,
                                List<StatusCard> statusCards, ItemsCard items, DocumentsCard documents,
                                PaymentsCard payments, HistoryCard history, SupplierCard supplier,
                                ConsigneeCard consignee, TermsCard terms, CommentCard comment, Dialogs dialogs,
                                String openDialog) {

    /** Whether a status card, the primary action or the More menu opens this dialog, so the page renders it. */
    public boolean offers(String dialogId) {
        boolean inCards = statusCards.stream().flatMap(card -> card.actions().stream())
                .anyMatch(action -> dialogId.equals(action.dialogId()));
        boolean primary = header.primary() != null && dialogId.equals(header.primary().dialogId());
        boolean reject = "reject-dialog".equals(dialogId) && header.more().reject().visible();
        return inCards || primary || reject;
    }

    /** visible: the role and the kind of delivery have this action at all; enabled: it runs now; reasonKey: why not. */
    public record ActionState(boolean visible, boolean enabled, String reasonKey) {

        public static ActionState hidden() {
            return new ActionState(false, false, null);
        }

        public static ActionState on() {
            return new ActionState(true, true, null);
        }

        public static ActionState off(String reasonKey) {
            return new ActionState(true, false, reasonKey);
        }
    }

    /**
     * externalIdNoteKey: what stands instead of a missing supplier number (ordering, sent unconfirmed), else null.
     * totalGross: null while the VAT is unset (DeliveryRules.grossOrNull), rendered as "—".
     */
    public record Header(String stateKey, String stateTone, String typeKey, String typeIcon, String supplierName,
                         String externalId, String externalIdHref, boolean provisional, String externalIdNoteKey,
                         String orderedAt, String dateKey, String date, int receivedAllocations, int totalAllocations,
                         boolean showProgress, String totalGross, PrimaryAction primary, MoreMenu more) {
    }

    /** href: a link (approval) or the no-JavaScript opener of dialogId; postAction: a POST without dialog (retry, reconcile). */
    public record PrimaryAction(String labelKey, String icon, String href, String postAction, String dialogId) {
    }

    public record MoreMenu(ActionState refresh, String refreshAction, ActionState reject, String rejectHref,
                           ActionState delete, String deleteHref) {

        public boolean shown() {
            return refresh.visible() || reject.visible() || delete.visible();
        }
    }

    /** tone is-bad/is-warn/is-info; reason: the supplier's own text (shown as code), or null. */
    public record StatusCard(String tone, String icon, String titleKey, String textKey, String reason,
                             List<CardAction> actions) {
    }

    /** selectPending: the opener first checks every waiting allocation ("Zaznacz wszystkie i usuń"). */
    public record CardAction(String labelKey, String dialogId, String href, boolean danger, boolean selectPending) {
    }

    public record ItemsCard(List<ProductRow> products, boolean selectable, String receivedColumnKey,
                            SelectionBar selection, String goodsNet, String goodsGross, int allocationCount,
                            int pendingCount) {

        public boolean anyMenu() {
            return products.stream().anyMatch(ProductRow::menu);
        }

        /** Whether any product's quantity can change now, so the page renders the quantity dialog. */
        public boolean anyQtyChange() {
            return products.stream().anyMatch(product -> product.changeQty().enabled());
        }
    }

    public record ProductRow(String name, String ean, String mfn, int receivedQty, int orderedQty, boolean complete,
                             String value, String unitCost, int minQty, ActionState changeQty, String qtyHref,
                             String historyHref, boolean menu, List<AllocationRow> allocations) {

        /** The ids of the allocation rows the product's toggle shows and hides (aria-controls takes a list). */
        public String allocationIds() {
            return String.join(" ", allocations.stream().map(row -> "alloc-" + row.index()).toList());
        }
    }

    /**
     * index: the position in the delivery's allocations, which is the index the hidden fields post under
     * (allocations[index]); checkbox: a waiting allocation on a page with the selection row; checked: preselected.
     */
    public record AllocationRow(int index, boolean warehouse, String orderShortId, String customer, String href,
                                boolean directToConsumer, int qty, boolean received, boolean checkbox, boolean checked,
                                String stateKey, String stateTone, String productName, AllocationFields fields) {
    }

    /** The values DeliveryAllocationsForm binds back for one allocation, as the hidden fields carry them. */
    public record AllocationFields(String orderId, String itemId, String keyName, String type, String name, int qty,
                                   String ean, String mfn, String deliveryId, boolean inAllocation, String unitCost) {
    }

    public record SelectionBar(ActionState receive, ActionState ship, ActionState merge, ActionState split,
                               ActionState remove, String removeMessageKey, List<MergeTarget> mergeTargets) {

        public boolean moveVisible() {
            return merge.visible() || split.visible();
        }
    }

    public record MergeTarget(String deliveryId, String shortId, String externalId, String estimatedDeliveryAt) {
    }

    public record DocumentsCard(boolean showInvoicePills, boolean invoiced, String invoiceTone, boolean synced,
                                ActionState link, String linkHref, String externalId, List<DocumentRow> rows,
                                String emptyKey) {
    }

    public record DocumentRow(String typeKey, String number, String href, boolean external, String issuedAt,
                              boolean invoice, String syncHref, String unlinkHref) {
    }

    /**
     * pillAmount: the argument of the overpaid/underpaid pill, else null. fields: every payment as the edit and remove
     * forms post it back (updatePayments replaces the whole list). expected, pending, sources feed "Dodaj wpłatę".
     * toPay, remaining: null while the VAT is unset (tax below 1.0), rendered as "—"; the pill keeps today's logic.
     */
    public record PaymentsCard(String pillKey, String pillTone, String pillAmount, String toPay, String paid,
                               String remaining, boolean remainingDue, String dueDate, int paymentTerms,
                               boolean editable, List<PaymentRow> rows, List<PaymentFields> fields, double expected,
                               Payment pending, List<OrderLabels.Option<PaymentSource>> sources) {
    }

    public record PaymentRow(int index, int number, String amount, boolean refund, boolean pending, String sourceKey,
                             String name, String referenceNo, String bankTransactionNo, String bankTransactionDate,
                             String fee, String dialogId, String editHref) {
    }

    public record PaymentFields(String source, String direction, String name, String amount, String fee,
                                String referenceNo, String bankTransactionNo, String bankTransactionDate) {
    }

    public record HistoryCard(List<EventRow> events) {

        public static final int VISIBLE = 3;

        public int visible() {
            return VISIBLE;
        }

        public List<EventRow> rest() {
            return events.size() <= VISIBLE ? List.of() : events.subList(VISIBLE, events.size());
        }
    }

    public record EventRow(String at, String labelKey) {
    }

    /** longNumber: a supplier number over 20 characters gets a full-width row that wraps anywhere (P7). */
    public record SupplierCard(String supplierName, String connectionKey, String counterparty, String externalId,
                               String externalIdHref, boolean provisional, String externalIdNoteKey,
                               boolean longNumber, String orderChoices, String deliveryAddress) {
    }

    /** name null: the locator had no answer, the card shows the shipment only. trackingNo, shippedAt: after shipping. */
    public record ConsigneeCard(String name, String street, String cityLine, String phone, String email,
                                String orderShortId, String orderHref, String shipmentTypeKey, String carrier,
                                String collectionPoint, String trackingNo, String shippedAt, String supplierStateKey) {
    }

    public record TermsCard(String orderedAt, String estimatedDeliveryAt, String receivedLabelKey, String receivedAt,
                            int paymentTerms, String goodsNet, String shippingNet, String paymentNet,
                            boolean reverseCharge, String vatPercent, String totalNet, String totalGross,
                            ActionState edit, String editHref) {
    }

    public record CommentCard(String text, ActionState edit, String editHref) {
    }

    /**
     * Values the dialogs start from: the suggested date of a manual confirmation, the shipment the customer chose,
     * the waiting allocations "Odbierz całość" lists, and the product whose quantity dialog is open without JavaScript.
     */
    public record Dialogs(String suggestedEstimatedDeliveryAt, String externalId, List<String> carrierOptions,
                          String shipmentType, String carrier, String collectionPoint, String shippedAtDefault,
                          String splitDate, List<PendingLine> pending, ProductRow qtyProduct, int receivedCount,
                          int allocationCount) {
    }

    public record PendingLine(String productName, int qty, boolean warehouse, String orderShortId, String customer) {
    }
}
