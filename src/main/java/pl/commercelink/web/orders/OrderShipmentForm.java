package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One shipment of an order as its edit form shows it: in the shipment's own dialog of the shipments card and on the
 * shipment page without JavaScript. index is the shipment's position in the order (null for a new one) and version a
 * fingerprint of the shipment the form was rendered from: a save or a removal whose shipment has changed since (a
 * tracking update, another operator) is refused instead of overwriting it.
 * <p>
 * A date and a time are separate fields: a date-only picker closes on the first click, where a date-and-time picker
 * waited for the time too. The time is optional; left empty it keeps the time already saved (the tracking fills it to
 * the minute), and a date entered for the first time starts at midnight. The time is typed ("14:30", "9.05"), so no
 * picker opens for it either. A blank date clears the moment, its time is then ignored.
 */
public record OrderShipmentForm(String orderId, Integer index, String version, ShipmentType type, String carrier,
                                String trackingNo, String collectionPointCode, String trackingUrl,
                                String shippedDate, String shippedTime, String deliveredDate, String deliveredTime,
                                List<String> carriers, Map<String, String> errors, String refusal) {

    private static final Pattern TIME = Pattern.compile("(\\d{1,2})[:.](\\d{2})");

    public OrderShipmentForm {
        type = type != null ? type : ShipmentType.Courier;
        carriers = carriers != null ? carriers : List.of();
        errors = errors != null ? errors : Map.of();
    }

    /** The form of the shipment at index, or of a new one when shipment is null. */
    public static OrderShipmentForm of(String orderId, Integer index, Shipment shipment, List<String> carriers) {
        if (shipment == null) {
            return new OrderShipmentForm(orderId, null, null, ShipmentType.Courier, null, null, null, null,
                    null, null, null, null, carriers, Map.of(), null);
        }
        return new OrderShipmentForm(orderId, index, version(shipment), shipment.getType(), shipment.getCarrier(),
                shipment.getTrackingNo(), shipment.getCollectionPointCode(), shipment.getTrackingUrl(),
                date(shipment.getShippedAt()), time(shipment.getShippedAt()),
                date(shipment.getDeliveredAt()), time(shipment.getDeliveredAt()), carriers, Map.of(), null);
    }

    /** What an edit is checked against: every field the form shows, the dates to the minute it shows them at. */
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

    /** Field id to message key, in the order of the form. */
    public Map<String, String> validate() {
        Map<String, String> found = new LinkedHashMap<>();
        // a new shipment with nothing in it would only hold the order back from Delivered (every shipment needs a
        // delivery date); the type alone is not a shipment
        if (isNew() && Stream.of(carrier, trackingNo, collectionPointCode, trackingUrl, shippedDate, deliveredDate)
                .allMatch(StringUtils::isBlank)) {
            found.put(field("trackingNo"), "order.shipments.error.empty");
        }
        checkMoment(found, "shippedDate", shippedDate, "shippedTime", shippedTime);
        checkMoment(found, "deliveredDate", deliveredDate, "deliveredTime", deliveredTime);
        return found;
    }

    /**
     * The shipment to store in place of saved (null for a new one). Its tracking subscription and courier order stay
     * with it while the tracking number is the same one. Call only after {@link #validate()} found nothing.
     */
    public Shipment toShipment(Shipment saved) {
        Shipment shipment = new Shipment(type);
        shipment.setCarrier(StringUtils.trimToNull(carrier));
        shipment.setTrackingNo(StringUtils.trimToNull(trackingNo));
        shipment.setCollectionPointCode(StringUtils.trimToNull(collectionPointCode));
        shipment.setTrackingUrl(StringUtils.trimToNull(trackingUrl));
        shipment.setShippedAt(moment(shippedDate, shippedTime, saved == null ? null : saved.getShippedAt()));
        shipment.setDeliveredAt(moment(deliveredDate, deliveredTime, saved == null ? null : saved.getDeliveredAt()));
        shipment.inheritTrackingSubscriptionFrom(saved);
        return shipment;
    }

    public OrderShipmentForm withErrors(Map<String, String> found) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, shippedTime, deliveredDate, deliveredTime, carriers, found, refusal);
    }

    /** A reason the whole form was refused (a closed order, a shipment changed meanwhile), already translated. */
    public OrderShipmentForm withRefusal(String text) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, shippedTime, deliveredDate, deliveredTime, carriers, errors, text);
    }

    /** A blank date clears the moment whatever its time says: the time field is prefilled, so it is rarely empty too. */
    private void checkMoment(Map<String, String> found, String dateField, String date, String timeField, String time) {
        if (StringUtils.isBlank(date)) {
            return;
        }
        if (parseDate(date) == null) {
            found.put(field(dateField), "order.shipments.error.date");
        }
        if (StringUtils.isNotBlank(time) && parseTime(time) == null) {
            found.put(field(timeField), "order.shipments.error.time");
        }
    }

    /**
     * The date with its time; no time keeps the saved one's (midnight when nothing was saved). A value equal to the
     * saved one to the minute keeps the saved value itself, seconds included, so re-saving an untouched form changes
     * nothing.
     */
    private static LocalDateTime moment(String date, String time, LocalDateTime saved) {
        LocalDate day = parseDate(date);
        if (day == null) {
            return null;
        }
        LocalTime at = StringUtils.isNotBlank(time) ? parseTime(time)
                : saved != null ? saved.toLocalTime() : LocalTime.MIDNIGHT;
        LocalDateTime value = day.atTime(at);
        if (saved != null && value.truncatedTo(ChronoUnit.MINUTES).equals(saved.truncatedTo(ChronoUnit.MINUTES))) {
            return saved;
        }
        return value;
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

    /** "14:30", "9:05", "9.05"; null for anything else, including 24:00 or 12:60. */
    static LocalTime parseTime(String value) {
        Matcher m = TIME.matcher(value.trim());
        if (!m.matches()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        return hour < 24 && minute < 60 ? LocalTime.of(hour, minute) : null;
    }

    private static String date(LocalDateTime value) {
        return value == null ? null : value.toLocalDate().toString();
    }

    private static String time(LocalDateTime value) {
        return value == null ? null : String.format("%02d:%02d", value.getHour(), value.getMinute());
    }

    private static String minutes(LocalDateTime value) {
        return value == null ? "" : value.truncatedTo(ChronoUnit.MINUTES).toString();
    }
}
