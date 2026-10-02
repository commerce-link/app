package pl.commercelink.web.deliveries.details;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.*;
import pl.commercelink.web.dtos.DeliveryTermsForm;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderFormats;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderPageModelFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Documents (§7), payments (§8), history (§9), the read-only side column (§10) and the values the dialogs start from. */
final class DeliveryCardsFactory {

    private static final String MANUALLY = "DELIVERY_ORDERED_MANUALLY";
    private static final Set<String> KNOWN_EVENTS = Set.of("DELIVERY_CREATED", "DELIVERY_ORDERED_AUTOMATICALLY",
            MANUALLY, "DELIVERY_PURCHASE_APPROVED", "DELIVERY_PURCHASE_RETRIED", "DELIVERY_ORDER_RECONCILED",
            "DELIVERY_ORDER_ID_CONFIRMED", "DELIVERY_ORDER_ID_UNCONFIRMED", "DELIVERY_UPDATED", "DELIVERY_DELAYED",
            "DELIVERY_ITEM_QTY_UPDATED", "DELIVERY_RECEIVED");
    private static final Set<DeliveryTrackingState> IN_STATUS_CARD = Set.of(DeliveryTrackingState.CANCELLED_BY_SUPPLIER,
            DeliveryTrackingState.SHIPPED_WITHOUT_DATA, DeliveryTrackingState.GIVEN_UP);
    private static final DateTimeFormatter DATE_TIME_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private DeliveryCardsFactory() {
    }

    static DocumentsCard documents(Delivery delivery, DeliveryViewer viewer, DeliveryLinks links) {
        boolean manageInvoices = viewer.storeAdmin() && !delivery.isAwaitingApproval();
        List<DocumentRow> rows = delivery.getDocuments().stream()
                .map(document -> documentRow(document, manageInvoices, links)).toList();
        String invoiceTone = delivery.isInvoiced() ? OrderLabels.OK
                : delivery.hasBeenReceived() ? OrderLabels.WARN : OrderLabels.NEUTRAL;
        boolean receiptToCome = !delivery.isDropship() && !delivery.hasBeenReceived();
        String emptyKey = receiptToCome
                ? (manageInvoices ? "deliveries.details.documents.empty.warehouse" : "deliveries.details.documents.empty.warehouse.readOnly")
                : (manageInvoices ? "deliveries.details.documents.empty.link" : "deliveries.details.documents.empty.readOnly");
        // the internal warehouse is not invoiced by anyone, like the FV/SYNC marks of the list (DeliveryRowMapper)
        return new DocumentsCard(!SupplierRegistry.WAREHOUSE.equals(delivery.getProvider()), delivery.isInvoiced(),
                invoiceTone, delivery.isSynced(), manageInvoices ? ActionState.on() : ActionState.hidden(),
                links.open("invoice"), StringUtils.trimToNull(delivery.getExternalDeliveryId()), rows, emptyKey);
    }

    private static DocumentRow documentRow(Document document, boolean manageInvoices, DeliveryLinks links) {
        boolean invoice = document.getType() == DocumentType.InvoiceVat;
        // an external link is typed by hand: only a web address becomes a link (design system, 2026-09-27)
        String href = document.isExternal() ? OrderPageModelFactory.safeWebUrl(document.getLink())
                : document.getType() != null && document.getType().isWarehouseDocument() && document.getId() != null
                ? links.warehouseDocument(document.getId()) : null;
        boolean act = invoice && manageInvoices && document.getId() != null;
        return new DocumentRow(OrderLabels.documentType(document.getType()), document.getNumber(), href,
                document.isExternal(), OrderFormats.date(document.getIssuedAt()), invoice,
                act ? links.syncPreview(document.getId()) : null, act ? links.unlinkInvoice(document.getId()) : null);
    }

    static PaymentsCard payments(Delivery delivery, DeliveryViewer viewer, DeliveryLinks links) {
        boolean editable = viewer.storeAdmin() && !delivery.isAwaitingApproval();
        List<Payment> payments = delivery.getPayments() == null ? List.of() : delivery.getPayments();
        List<PaymentRow> rows = new ArrayList<>();
        List<PaymentFields> fields = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            Payment payment = payments.get(i);
            // a refund from the supplier is typed with a minus sign (delivery.payment.direction.help) and stored as typed;
            // the direction is no guide: most refunds in production carry the dialog's default "Outgoing"
            rows.add(new PaymentRow(i, i + 1, Money.format(payment.getAmount()),
                    payment.getAmount() < 0, payment.isUnsettled(),
                    OrderLabels.paymentSource(payment.getSource()), paymentDetails(payment),
                    DeliveryPageModelFactory.dialogId("payment-" + i), editable ? links.open("payment-" + i) : null));
            fields.add(new PaymentFields(payment.getSource() == null ? null : payment.getSource().name(),
                    (payment.getDirection() == null ? PaymentDirection.Outgoing : payment.getDirection()).name(),
                    payment.getName(), Money.input(payment.getAmount()), Money.input(payment.getFee()),
                    payment.getReferenceNo(), payment.getBankTransactionNo(),
                    OrderFormats.isoDate(payment.getBankTransactionDate())));
        }
        double unpaid = delivery.getUnpaidAmount();
        String pillKey;
        String pillTone;
        String pillAmount = null;
        // the persisted "paid" flag first, as the old page did; the live amounts may disagree (side task 12)
        if (delivery.isPaid()) {
            pillKey = "deliveries.details.payments.paid";
            pillTone = OrderLabels.OK;
        } else if (delivery.isOverpaid()) {
            pillKey = "deliveries.details.payments.overpaid";
            // red, as the Payments list shows an overpayment since #255
            pillTone = OrderLabels.BAD;
            pillAmount = Money.format(-unpaid);
        } else if (delivery.isUnderpaid()) {
            pillKey = "deliveries.details.payments.underpaid";
            pillTone = OrderLabels.WARN;
            pillAmount = Money.format(unpaid);
        } else {
            pillKey = "deliveries.details.payments.unpaid";
            pillTone = OrderLabels.NEUTRAL;
        }
        // an unset VAT (tax below 1.0) has no gross to pay yet: "—" like every other gross amount (Task 7 ruling)
        String remaining = delivery.getTax() < 1.0 ? null : Money.format(Math.max(0, unpaid));
        return new PaymentsCard(pillKey, pillTone, pillAmount, DeliveryRules.grossOrNull(delivery, delivery.getTotalCost()),
                Money.format(delivery.getPaidAmount()), remaining, unpaid > 0.005,
                OrderFormats.date(delivery.getPaymentDueDate()), delivery.getPaymentTerms(), editable, rows, fields,
                Math.max(0, unpaid), delivery.getPendingPayment(),
                OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource));
    }

    // only the parts the payment has, so the line never starts with a separator nor stays as an empty paragraph
    private static List<PaymentDetail> paymentDetails(Payment payment) {
        List<PaymentDetail> details = new ArrayList<>();
        String name = StringUtils.trimToNull(payment.getName());
        String reference = StringUtils.trimToNull(payment.getReferenceNo());
        String operation = StringUtils.trimToNull(payment.getBankTransactionNo());
        String date = OrderFormats.date(payment.getBankTransactionDate());
        if (name != null) {
            details.add(PaymentDetail.text(name));
        }
        if (reference != null) {
            details.add(PaymentDetail.message("deliveries.details.payments.reference", reference));
        }
        if (operation != null) {
            details.add(new PaymentDetail(null, "deliveries.details.payments.operation", operation, false, date));
        } else if (date != null) {
            details.add(PaymentDetail.message("deliveries.details.payments.operationDate", date));
        }
        if (payment.getFee() > 0) {
            details.add(new PaymentDetail(null, "deliveries.details.payments.fee", Money.format(payment.getFee()), true, null));
        }
        return details;
    }

    static HistoryCard history(Delivery delivery) {
        List<Event> events = new ArrayList<>(delivery.getEvents());
        Collections.reverse(events);
        return new HistoryCard(events.stream()
                .map(event -> new EventRow(OrderFormats.dateTime(event.getCreatedAt()), eventKey(event.getName(), delivery.isDropship())))
                .toList());
    }

    private static String eventKey(String name, boolean dropship) {
        if (name == null || !KNOWN_EVENTS.contains(name)) {
            return "deliveries.history.event.other";
        }
        // receivedAt of a dropship delivery is the confirmed shipment to the customer
        return "DELIVERY_RECEIVED".equals(name) && dropship
                ? "deliveries.history.event.DELIVERY_RECEIVED.dropship" : "deliveries.history.event." + name;
    }

    static SupplierCard supplier(DeliveryPageData data) {
        Delivery delivery = data.delivery();
        String externalId = delivery.isOrderPending() ? null : StringUtils.trimToNull(delivery.getExternalDeliveryId());
        String noteKey = delivery.isOrderPending() ? "deliveries.details.supplier.number.pending"
                : externalId == null && delivery.isOrderDispatched() ? "deliveries.details.supplier.number.dispatched" : null;
        return new SupplierCard(data.supplierName(), connectionKey(delivery),
                StringUtils.trimToNull(delivery.getCounterpartyShortcut()), externalId,
                externalId == null ? null : OrderPageModelFactory.safeWebUrl(data.partnerSiteUrl()),
                delivery.isExternalDeliveryIdProvisional(), noteKey, externalId != null && externalId.length() > 20,
                StringUtils.trimToNull(delivery.getSupplierOrderChoicesLabel()),
                StringUtils.trimToNull(delivery.getDeliveryAddress()));
    }

    /**
     * How the delivery was ordered. The spec's rule (no mode + DELIVERY_ORDERED_MANUALLY) does not match the data: a
     * manual supplier has mode MANUAL and an order placed outside the system with an integrated supplier has mode OWN.
     * Only the purchase through the API sets purchaseRef, and "Potwierdź ręcznie" records DELIVERY_ORDERED_MANUALLY.
     */
    static String connectionKey(Delivery delivery) {
        if (delivery.getConnectionMode() == ConnectionMode.GLOBAL) {
            return "deliveries.details.supplier.connection.global";
        }
        if (delivery.getPurchaseRef() != null && !delivery.hasEvent(MANUALLY)) {
            return "deliveries.details.supplier.connection.own";
        }
        return "deliveries.details.supplier.connection.manual";
    }

    static ConsigneeCard consignee(DeliveryPageData data, DeliveryLinks links) {
        Delivery delivery = data.delivery();
        if (!delivery.isDropship()) {
            return null;
        }
        Order order = data.dropshipOrder();
        ShippingDetails shipping = order == null ? null : order.getShippingDetails();
        Shipment shipment = order == null ? null : order.firstShipment().orElse(null);
        boolean shipped = delivery.hasBeenReceived();
        Shipment parcel = shipped && order != null ? onlyParcel(order) : null;
        DeliveryTrackingState tracking = delivery.getTrackingView().effectiveState();
        boolean shownInStatusCard = !shipped && delivery.getOrderStatus() == null && IN_STATUS_CARD.contains(tracking);
        String supplierState = delivery.getOrderStatus() != null || shownInStatusCard
                || (shipped && tracking == DeliveryTrackingState.PENDING)
                ? null : "deliveries.dropship.tracking.state." + tracking.name();
        ConsigneeCard card = new ConsigneeCard(
                shipping == null ? null : StringUtils.trimToNull(shipping.getDisplayName()),
                shipping == null ? null : StringUtils.trimToNull(shipping.getStreetAndNumber()),
                shipping == null ? null : cityLine(shipping),
                shipping == null ? null : StringUtils.trimToNull(shipping.getPhone()),
                shipping == null ? null : StringUtils.trimToNull(shipping.getEmail()),
                order == null ? null : order.getShortenedOrderId(),
                order == null ? null : links.order(order.getOrderId()),
                shipment == null ? null : OrderLabels.shipmentType(shipment.getType()),
                shipment == null ? null : StringUtils.trimToNull(shipment.getCarrier()),
                shipment == null ? null : StringUtils.trimToNull(shipment.getCollectionPointCode()),
                parcel == null ? null : StringUtils.trimToNull(parcel.getTrackingNo()),
                parcel == null ? null : OrderFormats.moment(parcel.getShippedAt()),
                supplierState);
        // e.g. a failed purchase with no order resolved: a title over an empty body says nothing, so the card is left out
        return card.isEmpty() ? null : card;
    }

    /**
     * The parcel of a shipped dropship delivery. Parcels belong to the order, not to a delivery: with two dropship
     * deliveries on one order (two suppliers) or shipments confirmed in parts, the order's first parcel may be another
     * delivery's. Its number is shown only when the order has a single parcel with a tracking number.
     */
    private static Shipment onlyParcel(Order order) {
        List<Shipment> parcels = order.getShipments().stream()
                .filter(s -> StringUtils.isNotBlank(s.getTrackingNo()))
                .toList();
        return parcels.size() == 1 ? parcels.get(0) : null;
    }

    private static String cityLine(ShippingDetails shipping) {
        String place = Stream.of(shipping.getPostalCode(), shipping.getCity())
                .filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));
        String country = StringUtils.trimToNull(shipping.getCountry());
        String line = country == null ? place : (place.isEmpty() ? country : place + ", " + country);
        return StringUtils.trimToNull(line);
    }

    /**
     * VAT: an unset one (below 1.0, deliveries created by a purchase) is neither a percentage nor a gross total ("—");
     * only exactly 1.0 is the reverse charge at 0 %.
     */
    static TermsCard terms(Delivery delivery, DeliveryViewer viewer, DeliveryLinks links, double goodsNet) {
        ActionState edit = DeliveryRules.headerEditable(viewer, delivery) ? ActionState.on() : ActionState.hidden();
        return new TermsCard(OrderFormats.date(delivery.getOrderedAt()), OrderFormats.date(delivery.getEstimatedDeliveryAt()),
                delivery.isDropship() ? "deliveries.details.terms.shipped" : "deliveries.details.terms.received",
                delivery.hasBeenReceived() ? OrderFormats.date(delivery.getReceivedAt()) : null, delivery.getPaymentTerms(),
                Money.format(goodsNet), Money.format(delivery.getShippingCost()), Money.format(delivery.getPaymentCost()),
                delivery.getTax() == 1.0, StringUtils.trimToNull(DeliveryTermsForm.vatPercent(delivery.getTax())),
                Money.format(delivery.getTotalCost()), DeliveryRules.grossOrNull(delivery, delivery.getTotalCost()),
                edit, links.open("terms"));
    }

    static CommentCard comment(Delivery delivery, DeliveryViewer viewer, DeliveryLinks links) {
        ActionState edit = DeliveryRules.headerEditable(viewer, delivery) ? ActionState.on() : ActionState.hidden();
        return new CommentCard(StringUtils.trimToNull(delivery.getComment()), edit, links.open("comment"));
    }

    static Dialogs dialogs(DeliveryPageData data, ItemsCard items) {
        Delivery delivery = data.delivery();
        Order order = data.dropshipOrder();
        Shipment shipment = order == null ? null : order.firstShipment().orElse(null);
        List<PendingLine> pending = items.products().stream()
                .flatMap(product -> product.allocations().stream().filter(row -> !row.received())
                        .map(row -> new PendingLine(product.name(), row.qty(), row.warehouse(), row.orderShortId(), row.customer())))
                .toList();
        ProductRow qtyProduct = "qty".equals(data.openDialog())
                ? items.products().stream().filter(product -> Objects.equals(product.mfn(), data.openMfn())).findFirst().orElse(null)
                : null;
        ShipmentType type = shipment != null && shipment.getType() == ShipmentType.PickupPoint
                ? ShipmentType.PickupPoint : ShipmentType.Courier;
        return new Dialogs(OrderFormats.isoDate(data.suggestedEstimatedDeliveryAt()),
                StringUtils.trimToNull(delivery.getExternalDeliveryId()), data.carrierOptions(), type.name(),
                shipment == null ? null : StringUtils.trimToNull(shipment.getCarrier()),
                shipment == null ? null : StringUtils.trimToNull(shipment.getCollectionPointCode()),
                DATE_TIME_LOCAL.format(data.now()), OrderFormats.isoDate(delivery.getEstimatedDeliveryAt()), pending,
                qtyProduct, items.allocationCount() - items.pendingCount(), items.allocationCount());
    }
}
