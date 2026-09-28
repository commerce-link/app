package pl.commercelink.web.orders;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.products.ProductCatalog;
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
                             OrderClosingChecklist checklist, String checklistTitle, ItemsCard items,
                             ShipmentsCard shipments, DocumentsCard documents, PaymentsCard payments,
                             CustomerView customer, OrderSettingsView settings, FinancesView finances,
                             HistoryCard history, OrderStatusOptions statusOptions) {

    public record Header(String statusKey, String statusTone, boolean canChangeStatus, boolean completedAutomatically,
                         String clientName, String sourceName, String sourceTypeKey, String orderedAt, String total,
                         String fulfilmentTypeKey, String externalOrderId, RoutedSupplierView routedSupplier,
                         String splitFromShortId, String splitFromHref, String clientOrderUrl, PrimaryAction primaryAction,
                         String cardHref, String collectionHref, String itemHistoryHref, boolean canCancel,
                         boolean canDelete, String deleteMessage) {
    }

    public record PrimaryAction(String labelKey, String href, String icon) {
    }

    public record ItemsCard(List<OrderItemRow> products, List<OrderItemRow> services, int count, boolean selectable,
                            boolean canAddItems, String addItemsReasonKey, boolean canAddSerials,
                            List<SerialItemRow> serialItems, List<BulkActionButton> bulkActions, boolean bulkAvailable,
                            List<ProductCatalog> catalogs, List<SupplierLabelMap.Option> suppliers,
                            Map<String, SplitGroupPreviewDto> splitPreviews) {

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

    /** emptyKey: what the card says with no shipment, i.e. what the viewer can do about it now. */
    public record ShipmentsCard(List<ShipmentRow> rows, String emptyKey, boolean canCancelCourier,
                                List<OrderLabels.Option<ShipmentType>> types, List<String> carriers, List<Shipment> editable) {
    }

    public record ShipmentRow(String typeKey, String carrier, String trackingNo, String trackingUrl, String pickupPoint,
                              String shippedAt, String deliveredAt, String trackingKey, String trackingTone,
                              String trackingHelpKey) {
    }

    /** emptyKey takes the next type's label as its argument: "Issue" makes it, or "Add document" when it is typed by hand. */
    public record DocumentsCard(List<DocumentRow> rows, String emptyKey, boolean canAdd,
                                List<OrderLabels.Option<DocumentType>> manualTypes, DocumentType nextType, String nextTypeKey,
                                List<OrderLabels.Option<DocumentType>> issuable, boolean goodsIssue, boolean canIssue,
                                String today) {
    }

    public record DocumentRow(String typeKey, String number, String href, boolean external, String issuedAt,
                              boolean removable, String removeHref) {
    }

    /** unpaid never goes below zero: an order paid above its total shows overpaidAmount instead. */
    public record PaymentsCard(List<PaymentRow> rows, String paid, String unpaid, boolean unpaidDue, boolean overpaid,
                               String overpaidAmount, boolean canEdit, double expected, Payment pending,
                               List<OrderLabels.Option<PaymentSource>> sources, List<Payment> editable) {
    }

    /** pending: a payment recorded with no amount yet (the method is known, the money has not arrived). */
    public record PaymentRow(String amount, boolean refund, boolean pending, String sourceKey, String name,
                             String referenceNo, String bankTransactionNo, String bankTransactionDate, String fee) {
    }

    public record HistoryCard(List<EventRow> events, OrderReview review, String reviewStatusKey, String reviewRequestedAt,
                              boolean reviewEditable, List<OrderLabels.Option<OrderReviewStatus>> reviewStatuses) {

        public static final int VISIBLE = 5;

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
