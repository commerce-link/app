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
import pl.commercelink.shipping.CarrierNames;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Iban;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.orders.Money;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Everything the public order status page shows, resolved once in the controller so the template carries
 * no logic. Deliberately omits prices per item, costs and suppliers — the page is a bearer-token link that
 * customers forward freely. The order total and payment balance are shown: the customer needs them to pay.
 */
@Getter
public class ClientOrderView {

    // The offered days start at the customer's "today"; on a server in UTC the bare LocalDate.now() is still yesterday
    // for the first hours after a Polish midnight, and the server would accept that past day.
    private static final ZoneId CUSTOMER_ZONE = ZoneId.of("Europe/Warsaw");

    private final String orderId;
    private final String shortOrderId;
    private final LocalDateTime orderedAt;
    private final ClientOrderStage stage;
    private final boolean cancelled;
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
    private final boolean shippingAddressEditable;
    private final LocalDate preferredShippingAt;
    private final boolean preferredShippingEditable;
    private final boolean preferredShippingOffered;
    private final LocalDate preferredShippingWindowEnd;
    private final List<DayOfWeek> preferredShippingDays;
    private final String headlineKey;
    private final String leadKey;
    private final List<DayOption> preferredShippingOptions;
    private final BankView bank;
    private final List<ShipmentRow> shipmentRows;
    private final LocalDateTime readyForCollectionAt;

    private ClientOrderView(Order order, List<OrderItem> orderItems, Store store, CategoryLocalizer categoryLocalizer,
                            boolean shippingAddressEditable, boolean preferredShippingEditable,
                            LocalDate today) {
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
        this.deliveryMethod = orderItems.stream()
                .filter(item -> item.isService() && OrderItem.DELIVERY_CATEGORY.equals(item.getCategory()))
                .map(OrderItem::getName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        this.shipmentType = order.firstShipment().map(Shipment::getType).orElse(ShipmentType.Courier);
        this.collectionPointCode = order.firstShipment().map(Shipment::getCollectionPointCode).orElse(null);
        this.carrier = order.firstShipment().map(CarrierNames::forCustomer).orElse(null);
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
        this.contactEmail = store.getClientContactEmail();
        this.shippingAddressEditable = shippingAddressEditable;
        this.preferredShippingAt = order.getPreferredShippingAt();
        this.preferredShippingEditable = preferredShippingEditable;
        this.preferredShippingOffered = store.isClientPreferredShippingDateEnabled();
        this.preferredShippingWindowEnd = order.getPreferredShippingWindowEnd();
        this.preferredShippingDays = store.preferredShippingDaysFor(order.getShipmentType()).stream().sorted().toList();
        this.headlineKey = resolveHeadlineKey();
        this.shipmentRows = shipments.stream().map(ClientOrderView::toRow).toList();
        this.leadKey = resolveLeadKey();
        this.preferredShippingOptions = resolveDayOptions(order, store, today);
        this.bank = toBank(order);
        this.readyForCollectionAt = shipments.stream()
                .filter(shipment -> shipment.getType() == ShipmentType.PersonalCollection)
                .map(Shipment::getShippedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    public String getPreferredShippingDayNumbers() {
        return preferredShippingDays.stream().map(day -> String.valueOf(day.getValue())).collect(Collectors.joining(","));
    }

    public static ClientOrderView from(Order order, List<OrderItem> orderItems, Store store, CategoryLocalizer categoryLocalizer) {
        return new ClientOrderView(order, orderItems, store, categoryLocalizer, false, false, LocalDate.now(CUSTOMER_ZONE));
    }

    public static ClientOrderView from(Order order, List<OrderItem> orderItems, Store store, CategoryLocalizer categoryLocalizer,
                                       boolean shippingAddressEditable, boolean preferredShippingEditable) {
        return from(order, orderItems, store, categoryLocalizer, shippingAddressEditable, preferredShippingEditable, LocalDate.now(CUSTOMER_ZONE));
    }

    // "today" is a parameter so the offered days are deterministic in tests.
    public static ClientOrderView from(Order order, List<OrderItem> orderItems, Store store, CategoryLocalizer categoryLocalizer,
                                       boolean shippingAddressEditable, boolean preferredShippingEditable, LocalDate today) {
        return new ClientOrderView(order, orderItems, store, categoryLocalizer, shippingAddressEditable, preferredShippingEditable, today);
    }

    public boolean isBeforeDispatch() {
        return !cancelled && (stage == ClientOrderStage.Accepted || stage == ClientOrderStage.Assembly
                || stage == ClientOrderStage.Preparation);
    }

    public boolean isAsapSelected() {
        return preferredShippingAt == null;
    }

    public String getTotalFormatted() {
        return Money.format(totalPrice);
    }

    public String getPaidFormatted() {
        return Money.format(paidAmount);
    }

    public String getUnpaidFormatted() {
        return Money.format(unpaidAmount);
    }

    // The bare number for the "copy amount" button: no grouping, decimal point.
    public String getUnpaidRaw() {
        return Money.input(unpaidAmount);
    }

    public String getOverpaidFormatted() {
        return Money.format(overpaidAmount);
    }

    public int getProductCount() {
        return items.size();
    }

    public boolean hasEstimatedDates() {
        return estimatedAssemblyAt != null || estimatedShippingAt != null;
    }

    public boolean isPreferredShippingVisible() {
        return !cancelled && (preferredShippingAt != null || preferredShippingEditable);
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

    private String resolveHeadlineKey() {
        String prefix = "client.order.headline.";
        if (cancelled) {
            return prefix + "cancelled";
        }
        return prefix + switch (stage) {
            case Accepted -> "accepted";
            case Assembly -> "assembly";
            case Preparation -> "preparation";
            case Shipping -> personalCollection ? "collection" : "shipping";
            case Delivered -> personalCollection ? "collected" : "delivered";
        };
    }

    private String resolveLeadKey() {
        if (cancelled) {
            return null;
        }
        String prefix = "client.order.lead.";
        return prefix + switch (stage) {
            case Accepted -> "accepted";
            case Assembly -> resolveAssemblyLeadSuffix();
            case Preparation -> "preparation";
            case Shipping -> personalCollection ? "collection" : resolveShippingLeadSuffix();
            case Delivered -> "delivered";
        };
    }

    // Days from the later of the planned dispatch and today to the end of the window, on the weekdays the store
    // ships. A day saved earlier stays on the list even when it no longer fits, so the form never silently drops it.
    private List<DayOption> resolveDayOptions(Order order, Store store, LocalDate today) {
        if (estimatedShippingAt == null) {
            return List.of();
        }
        LocalDate from = estimatedShippingAt.isAfter(today) ? estimatedShippingAt : today;
        LocalDate to = order.getPreferredShippingWindowEnd();
        var allowed = store.preferredShippingDaysFor(order.getShipmentType());
        List<DayOption> options = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (allowed.contains(day.getDayOfWeek())) {
                options.add(new DayOption(day, day.equals(estimatedShippingAt), day.equals(preferredShippingAt)));
            }
        }
        if (preferredShippingAt != null && options.stream().noneMatch(DayOption::selected)) {
            options.add(new DayOption(preferredShippingAt, preferredShippingAt.equals(estimatedShippingAt), true));
            options.sort(Comparator.comparing(DayOption::date));
        }
        return List.copyOf(options);
    }

    private BankView toBank(Order order) {
        if (bankAccount == null) {
            return null;
        }
        String currency = StringUtils.isBlank(bankAccount.getCurrency()) || "PLN".equalsIgnoreCase(bankAccount.getCurrency())
                ? null : bankAccount.getCurrency();
        return new BankView(bankAccount.getAccountHolder(), Iban.grouped(bankAccount.getIban()),
                Iban.normalize(bankAccount.getIban()), bankAccount.getSwiftCode(), bankAccount.getBankName(),
                currency, order.getOrderId());
    }

    private static ShipmentRow toRow(Shipment shipment) {
        return new ShipmentRow(CarrierNames.forCustomer(shipment), shipment.getTrackingNo(),
                StringUtils.trimToNull(shipment.getTrackingUrl()), shipment.getShippedAt(), shipment.getDeliveredAt());
    }

    // "Check with the carrier" only makes sense when a tracking link is there to follow.
    private String resolveShippingLeadSuffix() {
        if (shipmentRows.isEmpty()) {
            return "shipping.noParcel";
        }
        return shipmentRows.stream().anyMatch(row -> row.trackingUrl() != null)
                ? "shipping" : "shipping.noTracking";
    }

    private String resolveAssemblyLeadSuffix() {
        boolean anyAwaitingDelivery = items.stream().anyMatch(item -> item.status() == ClientOrderItemStatus.AwaitingDelivery);
        if (anyAwaitingDelivery && assembledCount > 0) {
            return "assembly.partial";
        }
        if (anyAwaitingDelivery) {
            return "assembly.awaiting";
        }
        return "assembly";
    }

    // The pickup address is not stored on the order; the store's default pickup address (the same one POS
    // orders use) is the answer, with the registered company address as fallback. The collection protocol
    // takes its place from here too, so the customer signs for the address they were sent to.
    public static PickupAddress resolvePickupAddress(Store store) {
        ShippingDetails pickup = store.getDefaultPickupAddress().orElse(null);
        if (pickup != null) {
            return new PickupAddress(pickup.getCompanyName(), pickup.getStreetAndNumber(), pickup.getPostalCode(),
                    pickup.getCity(), pickup.getCountry());
        }
        BillingDetails company = store.getBillingDetails();
        if (company != null) {
            return new PickupAddress(company.getCompanyName(), company.getStreetAndNumber(), company.getPostalCode(),
                    company.getCity(), company.getCountry());
        }
        return null;
    }

    public record DayOption(LocalDate date, boolean planned, boolean selected) {
    }

    public record BankView(String holder, String ibanGrouped, String ibanRaw, String swift, String bankName,
                           String currency, String transferTitle) {
    }

    public record ShipmentRow(String carrier, String trackingNo, String trackingUrl, LocalDateTime shippedAt,
                              LocalDateTime deliveredAt) {
    }

    public record PickupAddress(String companyName, String streetAndNumber, String postalCode, String city,
                                String country) {
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
