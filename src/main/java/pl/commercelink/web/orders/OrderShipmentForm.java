package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * One shipment of an order as its edit form shows it: in the shipment's own dialog of the shipments card and on the
 * shipment page without JavaScript. index is the shipment's position in the order (null for a new one) and version a
 * fingerprint of the shipment the form was rendered from: a save or a removal whose shipment has changed since (a
 * tracking update, another operator) is refused instead of overwriting it.
 * <p>
 * The shipped and delivered moments are edited as dates only: the operator does not need the hour, and a date picker
 * closes on the first click. A date equal to the saved one keeps the saved moment with its time (the tracking and the
 * integrations store it to the minute); a new or changed date starts at midnight, which the card shows as the date
 * alone. A blank date clears the moment. Both are facts that already happened, so neither may be after today, and
 * the delivery not before the shipping; "today" is the operator's day, in Warsaw, whatever the server's zone.
 * <p>
 * courierOrder: the shipment has a courier order (a paid label whose number the carrier gave), so its type, carrier
 * and tracking number are shown read-only and a change of any is refused; "Cancel courier order" is the way to change
 * them.
 */
public record OrderShipmentForm(String orderId, Integer index, String version, ShipmentType type, String carrier,
                                String trackingNo, String collectionPointCode, String trackingUrl,
                                String shippedDate, String deliveredDate,
                                List<String> carriers, Map<String, String> errors, String refusal,
                                boolean courierOrder, Clock clock) {

    /** The operator's day: the store's customers and staff are in Poland, the server runs in UTC. */
    public static final ZoneId OPERATOR_ZONE = ZoneId.of("Europe/Warsaw");

    public OrderShipmentForm {
        type = type != null ? type : ShipmentType.Courier;
        carriers = carriers != null ? carriers : List.of();
        errors = errors != null ? errors : Map.of();
        clock = clock != null ? clock : Clock.system(OPERATOR_ZONE);
    }

    public OrderShipmentForm(String orderId, Integer index, String version, ShipmentType type, String carrier,
                             String trackingNo, String collectionPointCode, String trackingUrl,
                             String shippedDate, String deliveredDate,
                             List<String> carriers, Map<String, String> errors, String refusal) {
        this(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl, shippedDate,
                deliveredDate, carriers, errors, refusal, false, null);
    }

    /** The form of the shipment at index, or of a new one when shipment is null. */
    public static OrderShipmentForm of(String orderId, Integer index, Shipment shipment, List<String> carriers) {
        if (shipment == null) {
            return new OrderShipmentForm(orderId, null, null, ShipmentType.Courier, null, null, null, null,
                    null, null, carriers, Map.of(), null);
        }
        return new OrderShipmentForm(orderId, index, version(shipment), shipment.getType(), shipment.getCarrier(),
                shipment.getTrackingNo(), shipment.getCollectionPointCode(), shipment.getTrackingUrl(),
                date(shipment.getShippedAt()), date(shipment.getDeliveredAt()), carriers, Map.of(), null,
                shipment.getExternalId() != null, null);
    }

    /**
     * The form of "Add shipment". While the order's only shipment is a placeholder (Order.onlyPlaceholder) the new
     * shipment fills it, so the form starts from the customer's choice kept there: type, carrier, pickup point.
     */
    public static OrderShipmentForm blank(Order order, List<String> carriers) {
        return order.onlyPlaceholder()
                .map(p -> new OrderShipmentForm(order.getOrderId(), null, null, p.getType(), p.getCarrier(), null,
                        p.getCollectionPointCode(), null, null, null, carriers, Map.of(), null))
                .orElseGet(() -> of(order.getOrderId(), null, null, carriers));
    }

    /** The same form telling "today" by clock. */
    public OrderShipmentForm withClock(Clock clock) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, deliveredDate, carriers, errors, refusal, courierOrder, clock);
    }

    /** The operator's today, the latest date the date fields offer (their max), whatever zone clock is in. */
    public LocalDate today() {
        return now().toLocalDate();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), OPERATOR_ZONE);
    }

    /**
     * What an edit is checked against: every field the form shows, the dates to the minute. The form shows the day
     * only, but a same-day save keeps the saved moment, so a time the tracking changed meanwhile must refuse it too.
     */
    public static String version(Shipment shipment) {
        String fields = String.join("|", String.valueOf(shipment.getType()),
                Objects.toString(shipment.getCarrier(), ""), Objects.toString(shipment.getTrackingNo(), ""),
                Objects.toString(shipment.getCollectionPointCode(), ""), Objects.toString(shipment.getTrackingUrl(), ""),
                minutes(shipment.getShippedAt()), minutes(shipment.getDeliveredAt()));
        return Integer.toHexString(fields.hashCode());
    }

    public boolean isNew() {
        return index == null;
    }

    /** The shipment's number as the card counts them, from 1. */
    public int number() {
        return index == null ? 0 : index + 1;
    }

    /** "new" or the index: the part of the dialog's and the fields' ids that tells the forms of one page apart. */
    public String key() {
        return index == null ? "new" : String.valueOf(index);
    }

    public String dialogId() {
        return "shipment-dialog-" + key();
    }

    /** The id of a field: shipment-0-trackingNo. The posted name is the property alone. */
    public String field(String property) {
        return "shipment-" + key() + "-" + property;
    }

    /** The message key of the field's error, or null. */
    public String error(String property) {
        return errors.get(field(property));
    }

    /** The shipment types of the form's select. */
    public List<OrderLabels.Option<ShipmentType>> types() {
        return OrderLabels.Option.of(ShipmentType.values(), OrderLabels::shipmentType);
    }

    public boolean knownCarrier() {
        return StringUtils.isBlank(carrier) || carriers.contains(carrier);
    }

    /** {@link #validate(Shipment)} of a new shipment. */
    public Map<String, String> validate() {
        return validate(null);
    }

    /**
     * Field id to message key, in the order of the form. saved is the shipment the form edits (null for a new one): one
     * with a courier order keeps its type, carrier and tracking number.
     */
    public Map<String, String> validate(Shipment saved) {
        Map<String, String> found = new LinkedHashMap<>();
        // a new shipment with nothing in it would only hold the order back from Delivered (every shipment needs a
        // delivery date); the type alone is not a shipment
        if (isNew() && Stream.of(carrier, trackingNo, collectionPointCode, trackingUrl, shippedDate, deliveredDate)
                .allMatch(StringUtils::isBlank)) {
            found.put(field("trackingNo"), "order.shipments.error.empty");
        }
        if (saved != null && saved.getExternalId() != null) {
            if (type != saved.getType()) {
                found.put(field("type"), "order.shipments.error.courierLocked");
            }
            if (!Objects.equals(StringUtils.trimToNull(carrier), StringUtils.trimToNull(saved.getCarrier()))) {
                found.put(field("carrier"), "order.shipments.error.courierLocked");
            }
            if (!Objects.equals(StringUtils.trimToNull(trackingNo), StringUtils.trimToNull(saved.getTrackingNo()))) {
                found.put(field("trackingNo"), "order.shipments.error.courierLocked");
            }
        }
        LocalDate shipped = checkDate(found, "shippedDate", shippedDate);
        LocalDate delivered = checkDate(found, "deliveredDate", deliveredDate);
        if (shipped != null && delivered != null && delivered.isBefore(shipped)) {
            found.put(field("deliveredDate"), "order.shipments.error.deliveredBeforeShipped");
        }
        return found;
    }

    /**
     * The shipment to store in place of saved (null for a new one). Its courier order stays with it, its tracking
     * subscription while the tracking number is the same one. Call only after {@link #validate(Shipment)} found
     * nothing.
     */
    public Shipment toShipment(Shipment saved) {
        return toShipment(saved, now());
    }

    /** {@link #toShipment(Shipment)} at a given moment: {@code now} is what "today" is saved as. */
    Shipment toShipment(Shipment saved, LocalDateTime now) {
        Shipment shipment = new Shipment(type);
        shipment.setCarrier(StringUtils.trimToNull(carrier));
        shipment.setTrackingNo(StringUtils.trimToNull(trackingNo));
        shipment.setCollectionPointCode(StringUtils.trimToNull(collectionPointCode));
        shipment.setTrackingUrl(StringUtils.trimToNull(trackingUrl));
        shipment.setShippedAt(moment(shippedDate, saved == null ? null : saved.getShippedAt(), now));
        shipment.setDeliveredAt(moment(deliveredDate, saved == null ? null : saved.getDeliveredAt(), now));
        shipment.inheritTrackingSubscriptionFrom(saved);
        shipment.inheritCourierOrderFrom(saved);
        return shipment;
    }

    public OrderShipmentForm withErrors(Map<String, String> found) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, deliveredDate, carriers, found, refusal, courierOrder, clock);
    }

    /** A reason the whole form was refused (a closed order, a shipment changed meanwhile), already translated. */
    public OrderShipmentForm withRefusal(String text) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, deliveredDate, carriers, errors, text, courierOrder, clock);
    }

    /** The typed day, or null when blank or refused (a wrong date, one after today). */
    private LocalDate checkDate(Map<String, String> found, String dateField, String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        LocalDate day = parseDate(date);
        if (day == null) {
            found.put(field(dateField), "order.shipments.error.date");
            return null;
        }
        if (day.isAfter(today())) {
            found.put(field(dateField), "order.shipments.error.future");
            return null;
        }
        return day;
    }

    /**
     * The moment of the date: the saved one itself when the date is the saved day (its time and seconds kept, so
     * re-saving an untouched form changes nothing); today is saved as now, since it is being recorded as it happens and
     * the day's start would put it before anything switched on earlier today (an automatic e-receipt starts only for
     * orders delivered after the store switched e-receipts on); any other day is saved as its start.
     */
    private static LocalDateTime moment(String date, LocalDateTime saved, LocalDateTime now) {
        LocalDate day = parseDate(date);
        if (day == null) {
            return null;
        }
        if (saved != null && saved.toLocalDate().equals(day)) {
            return saved;
        }
        return day.equals(now.toLocalDate()) ? now.truncatedTo(ChronoUnit.SECONDS) : day.atStartOfDay();
    }

    private static LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String date(LocalDateTime value) {
        return value == null ? null : value.toLocalDate().toString();
    }

    private static String minutes(LocalDateTime value) {
        return value == null ? "" : value.truncatedTo(ChronoUnit.MINUTES).toString();
    }
}
