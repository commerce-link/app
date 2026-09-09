package pl.commercelink.web.dtos;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ClientOrderItemStatus;
import pl.commercelink.orders.ClientOrderStage;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.CategoryLocalizer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Everything the public order status page shows, resolved once in the controller so the template carries
 * no logic. Deliberately omits prices per item, costs, suppliers and payment balance — the page is a
 * bearer-token link that customers forward freely.
 */
@Getter
public class ClientOrderView {

    private final String orderId;
    private final String shortOrderId;
    private final LocalDateTime orderedAt;
    private final ClientOrderStage stage;
    private final boolean cancelled;
    private final String noteKey;
    private final List<ClientOrderItemView> items;
    private final long assembledCount;
    private final String deliveryMethod;
    private final ShipmentType shipmentType;
    private final String collectionPointCode;
    private final String carrier;
    private final boolean personalCollection;
    private final ShippingDetails shippingDetails;
    private final PickupAddress pickupAddress;
    private final List<Shipment> shipments;
    private final LocalDateTime shippedAt;
    private final LocalDateTime deliveredAt;
    private final LocalDate estimatedAssemblyAt;
    private final LocalDate estimatedShippingAt;
    private final double totalPrice;
    private final boolean paymentRecorded;
    private final double paidAmount;
    private final double unpaidAmount;
    private final double overpaidAmount;
    private final BankAccount bankAccount;
    private final String contactEmail;

    private ClientOrderView(Order order, List<OrderItem> orderItems, Store store, CategoryLocalizer categoryLocalizer) {
        this.orderId = order.getOrderId();
        this.shortOrderId = order.getShortenedOrderId();
        this.orderedAt = order.getOrderedAt();
        this.stage = ClientOrderStage.from(order.getStatus()).orElse(null);
        this.cancelled = stage == null;
        this.items = orderItems.stream()
                .filter(OrderItem::isProduct)
                .sorted(Comparator.comparingInt(OrderItem::getPosition))
                .map(item -> ClientOrderItemView.from(item, categoryLocalizer))
                .toList();
        this.assembledCount = items.stream().filter(item -> item.status() == ClientOrderItemStatus.Assembled).count();
        this.personalCollection = order.isPersonalCollection();
        this.noteKey = resolveNoteKey();
        this.deliveryMethod = orderItems.stream()
                .filter(item -> item.isService() && OrderItem.DELIVERY_CATEGORY.equals(item.getCategory()))
                .map(OrderItem::getName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        this.shipmentType = order.firstShipment().map(Shipment::getType).orElse(ShipmentType.Courier);
        this.collectionPointCode = order.firstShipment().map(Shipment::getCollectionPointCode).orElse(null);
        this.carrier = order.firstShipment().map(Shipment::getCarrier).filter(StringUtils::isNotBlank).orElse(null);
        this.shippingDetails = order.getShippingDetails();
        this.pickupAddress = personalCollection ? resolvePickupAddress(store) : null;
        this.shipments = order.getShipments().stream()
                .filter(shipment -> shipment.hasShippingData() || shipment.hasCollectionData())
                .toList();
        this.shippedAt = shipments.stream().map(Shipment::getShippedAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        this.deliveredAt = order.isDelivered()
                ? shipments.stream().map(Shipment::getDeliveredAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null)
                : null;
        this.estimatedAssemblyAt = order.getEstimatedAssemblyAt();
        this.estimatedShippingAt = order.getEstimatedShippingAt();
        this.totalPrice = order.getTotalPrice();
        this.paymentRecorded = order.getPayments().stream().anyMatch(payment -> payment.getAmount() > 0);
        this.paidAmount = order.getPaidAmount();
        this.unpaidAmount = Math.max(0, order.getUnpaidAmount());
        this.overpaidAmount = Math.max(0, -order.getUnpaidAmount());
        this.bankAccount = store.getDefaultBankAccount();
        this.contactEmail = resolveContactEmail(store);
    }

    public static ClientOrderView from(Order order, List<OrderItem> orderItems, Store store, CategoryLocalizer categoryLocalizer) {
        return new ClientOrderView(order, orderItems, store, categoryLocalizer);
    }

    public int getProductCount() {
        return items.size();
    }

    public boolean hasEstimatedDates() {
        return estimatedAssemblyAt != null || estimatedShippingAt != null;
    }

    public boolean isFullyPaid() {
        return unpaidAmount < 0.01 && overpaidAmount < 0.01;
    }

    public boolean isOverpaid() {
        return overpaidAmount >= 0.01;
    }

    public boolean isPaymentDue() {
        return paymentRecorded && !isFullyPaid() && !isOverpaid();
    }

    public boolean isBankAccountVisible() {
        return isPaymentDue() && bankAccount != null;
    }

    public boolean isPickupPoint() {
        return shipmentType == ShipmentType.PickupPoint;
    }

    // Nothing gets dispatched for personal collection; the readiness date is already on the progress bar.
    public boolean isShipmentsVisible() {
        return !cancelled && !personalCollection;
    }

    public boolean isStageDone(ClientOrderStage candidate) {
        return candidate.isReachedBy(stage);
    }

    public boolean isStageCurrent(ClientOrderStage candidate) {
        return candidate == stage;
    }

    private String resolveNoteKey() {
        if (cancelled) {
            return "client.order.note.cancelled";
        }
        return switch (stage) {
            case Accepted -> "client.order.note.accepted";
            case Assembly -> resolveAssemblyNoteKey();
            case Preparation -> "client.order.note.preparation";
            case Shipping -> personalCollection ? "client.order.note.ready.for.collection" : "client.order.note.shipping";
            case Delivered -> "client.order.note.delivered";
        };
    }

    private String resolveAssemblyNoteKey() {
        boolean anyAwaitingDelivery = items.stream().anyMatch(item -> item.status() == ClientOrderItemStatus.AwaitingDelivery);
        if (anyAwaitingDelivery && assembledCount > 0) {
            return "client.order.note.assembly.partial";
        }
        if (anyAwaitingDelivery) {
            return "client.order.note.assembly.awaiting";
        }
        return "client.order.note.assembly";
    }

    // The pickup address is not stored on the order; the store's default pickup address (the same one POS
    // orders use) is the answer, with the registered company address as fallback.
    private static PickupAddress resolvePickupAddress(Store store) {
        ShippingDetails pickup = store.getDefaultPickupAddress().orElse(null);
        if (pickup != null) {
            return new PickupAddress(pickup.getCompanyName(), pickup.getStreetAndNumber(), pickup.getPostalCode(), pickup.getCity());
        }
        BillingDetails company = store.getBillingDetails();
        if (company != null) {
            return new PickupAddress(company.getCompanyName(), company.getStreetAndNumber(), company.getPostalCode(), company.getCity());
        }
        return null;
    }

    public record PickupAddress(String companyName, String streetAndNumber, String postalCode, String city) {
    }

    private static String resolveContactEmail(Store store) {
        ClientNotificationsConfiguration notifications = store.getClientNotificationsConfiguration();
        if (notifications != null && isNotBlank(notifications.getReplyToEmail())) {
            return notifications.getReplyToEmail();
        }
        if (store.getBillingDetails() != null && isNotBlank(store.getBillingDetails().getEmail())) {
            return store.getBillingDetails().getEmail();
        }
        return null;
    }

    public record ClientOrderItemView(String name, String category, int qty, ClientOrderItemStatus status) {

        static ClientOrderItemView from(OrderItem item, CategoryLocalizer categoryLocalizer) {
            return new ClientOrderItemView(
                    item.getName(),
                    categoryLocalizer.localize(item.getCategory(), "singular"),
                    item.getQty(),
                    ClientOrderItemStatus.from(item.getStatus())
            );
        }
    }
}
