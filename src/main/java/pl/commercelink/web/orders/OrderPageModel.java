package pl.commercelink.web.orders;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.receipts.ReceiptPageProblem;
import pl.commercelink.web.dtos.RoutedSupplierView;
import pl.commercelink.web.dtos.SplitGroupPreviewDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Everything the order details page shows, worked out once. closed = Completed or Cancelled; readOnly =
 * closed or a super admin looking at a store's order. The template only prints.
 */
public record OrderPageModel(String orderId, String shortId, String backHref, boolean closed, boolean readOnly,
                             boolean superAdmin, boolean admin, String storeName, Header header,
                             ItemsCard items,
                             ShipmentsCard shipments, DocumentsCard documents, PaymentsCard payments,
                             CustomerView customer, OrderSettingsView settings, FinancesView finances,
                             HistoryCard history, OrderStatusOptions statusOptions) {

    /**
     * cancelLockedKey: why "Cancel order" is greyed although the order could otherwise be cancelled (an e-receipt
     * being issued), null for the general reason; cancelMessage: the confirmation, with the fiscalised-receipt warning.
     */
    public record Header(String statusKey, String statusTone, boolean canChangeStatus, boolean completedAutomatically,
                         String clientName, String sourceName, String sourceTypeKey, String orderedAt, String total,
                         String fulfilmentTypeShortKey, String fulfilmentTypeIcon, String externalOrderId, RoutedSupplierView routedSupplier,
                         String splitFromShortId, String splitFromHref, String clientOrderUrl, PrimaryAction primaryAction,
                         String cardHref, String collectionHref, String itemHistoryHref, boolean canCancel,
                         String cancelLockedKey, String cancelMessage, boolean canDelete, String deleteMessage) {
    }

    public record PrimaryAction(String labelKey, String href, String icon) {
    }

    public record ItemsCard(List<OrderItemRow> products, List<OrderItemRow> services, int count, boolean selectable,
                            boolean canAddItems, String addItemsReasonKey, boolean canAddSerials,
                            List<SerialItemRow> serialItems, List<BulkActionButton> bulkActions, boolean bulkAvailable,
                            List<ProductCatalog> catalogs, List<SupplierLabelMap.Option> suppliers,
                            Map<String, SplitGroupPreviewDto> splitPreviews) {

        /**
         * Whether addItemsReasonKey is a whole sentence shown as it is ("Trwa wystawianie e-paragonu — pozycji nie
         * dodasz."), not a short reason after the "Dodawanie pozycji:" prefix.
         */
        public boolean addItemsReasonIsSentence() {
            return addItemsReasonKey != null && addItemsReasonKey.startsWith("order.items.add.locked.receipt");
        }

        /** The drop-downs of the selection row, each with its actions in the order of BulkAction; none is empty. */
        public List<BulkMenu> bulkMenus() {
            return Arrays.stream(BulkAction.Menu.values())
                    .map(menu -> new BulkMenu(menu, bulkActions.stream().filter(b -> b.action().menu() == menu).toList()))
                    .filter(menu -> !menu.actions().isEmpty())
                    .toList();
        }

        /** The action standing on its own at the end of the selection row (REMOVE), or null when the order offers none. */
        public BulkActionButton bulkStandalone() {
            return bulkActions.stream().filter(b -> b.action().menu() == null).findFirst().orElse(null);
        }
    }

    public record BulkMenu(BulkAction.Menu menu, List<BulkActionButton> actions) {
    }

    /** The serial-number dialog needs no cost, so it gets a slim row instead of the raw OrderItem. */
    public record SerialItemRow(String itemId, String name, String mfn, int qty, String deliveryLabel, String serialNo) {
    }

    /** reasonKey/shortReasonKey: the sentence and the few words saying why the action is unavailable, or null. */
    public record BulkActionButton(BulkAction action, boolean available, String reasonKey, String shortReasonKey, String href) {

        static BulkActionButton of(BulkAction action, BulkReason reason, String href) {
            return new BulkActionButton(action, reason == null, reason == null ? null : reason.key(),
                    reason == null ? null : reason.shortKey(), href);
        }
    }

    /**
     * emptyKey: what the card says with no shipment, i.e. what the viewer can do about it now. forms: one edit form per
     * shipment, in the order of rows; blank: the form of "Add shipment". Both empty/null on a read-only page.
     */
    public record ShipmentsCard(List<ShipmentRow> rows, String emptyKey, boolean canCancelCourier,
                                List<OrderShipmentForm> forms, OrderShipmentForm blank) {
    }

    /**
     * number counts from 1. editHref leads to the shipment page (its dialog, dialogId, intercepts it) and removeHref to
     * the removal confirmation; both null on a read-only page. For a shipment of a delivered order, one with a delivery
     * date and one with a courier order (cancelled with "Cancel courier order" instead) removeHref is null and
     * removeReasonKey says why, so "Remove" shows greyed with that reason instead of disappearing. The only shipment
     * with nothing but the delivery choice has neither: removing it would change nothing. removeMessageKey and
     * removeActionKey are the confirmation's text and button, which say when the removal delivers the order.
     */
    public record ShipmentRow(int number, String typeKey, String carrier, String trackingNo, String trackingUrl,
                              String pickupPoint, String shippedAt, String deliveredAt, String trackingKey,
                              String trackingTone, String trackingHelpKey, String dialogId, String editHref,
                              String removeHref, String removeReasonKey, String removeMessageKey,
                              String removeActionKey) {
    }

    /**
     * emptyKey takes the next type's label as its argument: "Issue" makes it, or "Add document" when it is typed by hand.
     * receipt: the order's e-receipt, or null when it has no attempt; its document is not repeated in rows.
     * addLockedKey: why "Add document" shows greyed (an e-receipt being issued), null when it is available or simply
     * absent. canIssueReceipt: "E-paragon" in the "Issue" menu; issueReceiptBlockedKey: it is shown greyed with this
     * reason (a POS sale without the customer's e-mail), null when it can run.
     */
    public record DocumentsCard(List<DocumentRow> rows, ReceiptRow receipt, String emptyKey, boolean canAdd,
                                String addLockedKey, List<OrderLabels.Option<DocumentType>> manualTypes,
                                DocumentType nextType, String nextTypeKey, List<OrderLabels.Option<DocumentType>> issuable,
                                boolean goodsIssue, boolean canIssueReceipt, String issueReceiptBlockedKey,
                                boolean canIssue, String today,
                                List<ReceiptCloseForm> closeForms) {

        /** No document row and no e-receipt: the card shows its empty text. */
        public boolean isEmpty() {
            return rows.isEmpty() && receipt == null;
        }
    }

    /**
     * The order's e-receipt as one row of the documents card: its newest attempt, with the earlier (dead, superseded)
     * attempts under it. number and href once known (href only a web address); dateKey/date say when it was
     * fiscalised or closed by hand; emailKey whether the buyer's e-mail went out or was skipped; problem what went
     * wrong, what the operator should do and the provider's technical hints, null without a problem; problemTone the
     * colour of its cause, that of the pill (ReceiptOrderView.Row#problemTone).
     * The actions are false on a super admin's page; closeHref is the "Zamknij ręcznie" page without JavaScript,
     * closeDialogId its dialog. settled: the attempt fiscalised nothing but the order got its sale document another way
     * (a receipt from the shop's cash register, an invoice) or was cancelled, so the row says nothing is needed, with
     * settledOutcome (why the attempt stopped) when known; settledKey is that sentence's message key, null when not
     * settled. reissueBlockedKey: "Wystaw ponownie" is shown greyed with this reason (a POS sale without the customer's
     * e-mail), null when it can run; reissueConfirmKey is its confirmation text. attachingKey: the receipt is
     * fiscalised but its document is not on the order yet (the locks say so too), null otherwise.
     */
    public record ReceiptRow(String key, int attemptNo, String number, String href, String statusKey, String statusTone,
                             String dateKey, String date, String emailKey, ReceiptPageProblem problem,
                             String problemTone, boolean canCheck,
                             boolean canResendEmail, boolean canClose, boolean canReissue, String reissueBlockedKey,
                             String reissueConfirmKey, String closeDialogId, String closeHref,
                             List<ReceiptEarlierRow> earlier, boolean settled, String settledOutcome, String settledKey,
                             String attachingKey) {

        /** Whether the row offers any action at all (the actions column is left out otherwise). */
        public boolean hasActions() {
            return canCheck || canResendEmail || canClose || canReissue;
        }
    }

    /** An earlier attempt: dead, superseded by a newer one, listed without actions. outcome: why it fiscalised nothing. */
    public record ReceiptEarlierRow(int attemptNo, String statusKey, String statusTone, String outcome) {
    }

    public record DocumentRow(String typeKey, String number, String href, boolean external, String issuedAt,
                              boolean removable, String removeHref) {
    }

    /**
     * unpaid never goes below zero: an order paid above its total shows overpaidAmount instead. forms: one edit form per
     * payment, in the order of rows, empty on a read-only page. expected, pending and sources feed the shared
     * "Dodaj wpłatę" dialog.
     */
    public record PaymentsCard(List<PaymentRow> rows, String paid, String unpaid, boolean unpaidDue, boolean overpaid,
                               String overpaidAmount, double expected, Payment pending,
                               List<OrderLabels.Option<PaymentSource>> sources, List<OrderPaymentForm> forms) {
    }

    /**
     * pending: a payment recorded with no amount yet (the method is known, the money has not arrived). number counts
     * from 1. editHref leads to the payment page (its dialog, dialogId, intercepts it) and removeHref to the removal
     * confirmation; both null on a read-only page. removeMessageKey is the confirmation's text: removing the only
     * payment says that a pending one with the same method stays. The pending payment has no removeHref and
     * removeReasonKey says why, so "Remove" shows greyed with that reason.
     */
    public record PaymentRow(int number, String amount, boolean refund, boolean pending, String sourceKey, String name,
                             String referenceNo, String bankTransactionNo, String bankTransactionDate, String fee,
                             String dialogId, String editHref, String removeHref, String removeMessageKey,
                             String removeReasonKey) {
    }

    public record HistoryCard(List<EventRow> events, OrderReview review, String reviewStatusKey, String reviewRequestedAt,
                              boolean reviewEditable, List<OrderLabels.Option<OrderReviewStatus>> reviewStatuses) {

        public static final int VISIBLE = 3;

        /** How many of the newest events show before the "show earlier events" node; the rest stay in the same list, hidden by script. */
        public int visible() {
            return VISIBLE;
        }

        public List<EventRow> rest() {
            return events.size() <= VISIBLE ? List.of() : events.subList(VISIBLE, events.size());
        }

        /** The label of the "show earlier events" node, in the plural form that fits the number of hidden events. */
        public String moreKey() {
            return "order.history.more." + PluralForm.of(rest().size());
        }
    }

    /** titleKey, with argKey (a message key) or arg (plain text) as its only argument when present. */
    public record EventRow(String at, String titleKey, String argKey, String arg) {
    }
}
