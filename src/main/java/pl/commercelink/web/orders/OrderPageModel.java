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

import java.util.List;
import java.util.Map;

/**
 * Everything the order details page shows, worked out once (spec §5.2). closed = Completed or Cancelled; readOnly =
 * closed or a super admin looking at a store's order (P12). The template only prints.
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
                         boolean canDelete, String deleteMessageKey, String marketplaceName) {
    }

    public record PrimaryAction(String labelKey, String href, String icon) {
    }

    public record ItemsCard(List<OrderItemRow> products, List<OrderItemRow> services, int count, boolean selectable,
                            boolean canAddItems, String addItemsReasonKey, boolean canAddSerials,
                            List<SerialItemRow> serialItems, List<BulkActionButton> bulkActions, boolean bulkAvailable,
                            List<ProductCatalog> catalogs, List<SupplierLabelMap.Option> suppliers,
                            Map<String, SplitGroupPreviewDto> splitPreviews) {
    }

    /** B10: the serial-number dialog needs no cost, so it gets a slim row instead of the raw OrderItem. */
    public record SerialItemRow(String itemId, String name, String mfn, int qty, String deliveryLabel, String serialNo) {
    }

    public record BulkActionButton(BulkAction action, boolean available, String reasonKey, String href) {
    }

    public record ShipmentsCard(List<ShipmentRow> rows, boolean tracked, boolean canEdit, boolean canCancelCourier,
                                List<OrderLabels.Option<ShipmentType>> types, List<String> carriers, List<Shipment> editable) {
    }

    public record ShipmentRow(String typeKey, String carrier, String trackingNo, String trackingUrl, String pickupPoint,
                              String shippedAt, String deliveredAt, String trackingKey) {
    }

    public record DocumentsCard(List<DocumentRow> rows, boolean canAdd, List<OrderLabels.Option<DocumentType>> manualTypes,
                                DocumentType nextType, String nextTypeKey, List<OrderLabels.Option<DocumentType>> issuable,
                                boolean goodsIssue, boolean canIssue, String today) {
    }

    public record DocumentRow(String typeKey, String number, String href, boolean external, String issuedAt,
                              boolean removable, String removeHref) {
    }

    public record PaymentsCard(List<PaymentRow> rows, String paid, String unpaid, boolean unpaidDue, boolean canAdd,
                               boolean canEdit, double expected, Payment pending, List<OrderLabels.Option<PaymentSource>> sources,
                               List<Payment> editable) {
    }

    public record PaymentRow(String amount, boolean refund, String sourceKey, String name, String referenceNo,
                             String bankTransactionNo, String bankTransactionDate, String fee) {
    }

    public record HistoryCard(List<EventRow> events, OrderReview review, String reviewStatusKey, String reviewRequestedAt,
                              boolean reviewEditable, List<OrderLabels.Option<OrderReviewStatus>> reviewStatuses) {

        public static final int VISIBLE = 5;

        public List<EventRow> first() {
            return events.subList(0, Math.min(VISIBLE, events.size()));
        }

        public List<EventRow> rest() {
            return events.size() <= VISIBLE ? List.of() : events.subList(VISIBLE, events.size());
        }
    }

    /** titleKey, with argKey (a message key) or arg (plain text) as its only argument when present. */
    public record EventRow(String at, String titleKey, String argKey, String arg) {
    }
}
