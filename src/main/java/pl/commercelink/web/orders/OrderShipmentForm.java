package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * alone. A blank date clears the moment.
 */
public record OrderShipmentForm(String orderId, Integer index, String version, ShipmentType type, String carrier,
                                String trackingNo, String collectionPointCode, String trackingUrl,
                                String shippedDate, String deliveredDate,
                                List<String> carriers, Map<String, String> errors, String refusal) {

    public OrderShipmentForm {
        type = type != null ? type : ShipmentType.Courier;
        carriers = carriers != null ? carriers : List.of();
        errors = errors != null ? errors : Map.of();
    }

    /** The form of the shipment at index, or of a new one when shipment is null. */
    public static OrderShipmentForm of(String orderId, Integer index, Shipment shipment, List<String> carriers) {
        if (shipment == null) {
            return new OrderShipmentForm(orderId, null, null, ShipmentType.Courier, null, null, null, null,
                    null, null, carriers, Map.of(), null);
        }
        return new OrderShipmentForm(orderId, index, version(shipment), shipment.getType(), shipment.getCarrier(),
                shipment.getTrackingNo(), shipment.getCollectionPointCode(), shipment.getTrackingUrl(),
                date(shipment.getShippedAt()), date(shipment.getDeliveredAt()), carriers, Map.of(), null);
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

    /** Field id to message key, in the order of the form. */
    public Map<String, String> validate() {
        Map<String, String> found = new LinkedHashMap<>();
        // a new shipment with nothing in it would only hold the order back from Delivered (every shipment needs a
        // delivery date); the type alone is not a shipment
        if (isNew() && Stream.of(carrier, trackingNo, collectionPointCode, trackingUrl, shippedDate, deliveredDate)
                .allMatch(StringUtils::isBlank)) {
            found.put(field("trackingNo"), "order.shipments.error.empty");
        }
        checkDate(found, "shippedDate", shippedDate);
        checkDate(found, "deliveredDate", deliveredDate);
        return found;
    }

    /**
     * The shipment to store in place of saved (null for a new one). Its tracking subscription and courier order stay
     * with it while the tracking number is the same one. Call only after {@link #validate()} found nothing.
     */
    public Shipment toShipment(Shipment saved) {
        return toShipment(saved, LocalDateTime.now());
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
        return shipment;
    }

    public OrderShipmentForm withErrors(Map<String, String> found) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, deliveredDate, carriers, found, refusal);
    }

    /** A reason the whole form was refused (a closed order, a shipment changed meanwhile), already translated. */
    public OrderShipmentForm withRefusal(String text) {
        return new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo, collectionPointCode, trackingUrl,
                shippedDate, deliveredDate, carriers, errors, text);
    }

    private void checkDate(Map<String, String> found, String dateField, String date) {
        if (StringUtils.isNotBlank(date) && parseDate(date) == null) {
            found.put(field(dateField), "order.shipments.error.date");
        }
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
