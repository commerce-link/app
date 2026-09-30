package pl.commercelink.web.orders;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.warehouse.api.ItemCondition;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Message keys and pill tones of the enums shown on the order screens: templates read enum text and tone only through here. */
public final class OrderLabels {

    public static final String OK = "is-ok";
    public static final String WARN = "is-warn";
    public static final String INFO = "is-info";
    public static final String BAD = "is-bad";
    public static final String NEUTRAL = "is-neutral";

    /** The text shipping-furgonetka stores when Furgonetka never got the cancel command (commandNotExists). */
    static final String CANCEL_NOT_RECEIVED = "Furgonetka did not receive the cancel command";

    private OrderLabels() {
    }

    /** An enum value paired with its message key: a template reads labelKey, it never builds "Type." + name. */
    public record Option<T>(T value, String labelKey) {

        public static <T> List<Option<T>> of(T[] values, Function<T, String> labelKey) {
            return Arrays.stream(values).map(v -> new Option<>(v, labelKey.apply(v))).toList();
        }

        public static <T> List<Option<T>> of(List<T> values, Function<T, String> labelKey) {
            return values.stream().map(v -> new Option<>(v, labelKey.apply(v))).toList();
        }
    }

    public static String status(OrderStatus status) {
        return status == null ? null : "OrderStatus." + status.name();
    }

    /** Matches {@code OrderRowMapper.statusTone} exactly, so the details pill and the list pill never disagree. */
    public static String tone(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case Blocked -> BAD;
            case Delivered, Completed -> OK;
            case Cancelled -> NEUTRAL;
            default -> INFO;
        };
    }

    public static String itemStatus(FulfilmentStatus status) {
        return status == null ? null : "FulfilmentStatus." + status.name();
    }

    public static String tone(FulfilmentStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            // waiting states neutral/info, trouble warn/bad
            case New -> NEUTRAL;
            case Allocation, Ordered, Reserved -> INFO;
            case Delivered -> OK;
            case InRMA, Returned, Replaced, InExternalService -> WARN;
            case Destroyed -> BAD;
        };
    }

    public static String sourceType(OrderSourceType type) {
        return type == null ? null : "order.source.type." + type.name();
    }

    public static String fulfilmentType(FulfilmentType type) {
        return type == null ? null : "store.fulfilment.type." + type.name();
    }

    /** Short label for the read-only places (record header, settings card) that pair it with a decorative icon. */
    public static String fulfilmentTypeShort(FulfilmentType type) {
        return type == null ? null : "order.fulfilment.short." + type.name();
    }

    /** Font Awesome class of the decorative icon paired with {@link #fulfilmentTypeShort}; the text carries the meaning. */
    public static String fulfilmentTypeIcon(FulfilmentType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case WarehouseFulfilment -> "fa-warehouse";
            case DirectToConsumer -> "fa-truck";
        };
    }

    public static String shipmentType(ShipmentType type) {
        return type == null ? null : "ShipmentType." + type.name();
    }

    public static String documentType(DocumentType type) {
        return type == null ? null : "DocumentType." + type.name();
    }

    public static String paymentSource(PaymentSource source) {
        return source == null ? null : "PaymentSource." + source.name();
    }

    public static String reviewStatus(OrderReviewStatus status) {
        return status == null ? null : "OrderReviewStatus." + status.name();
    }

    public static String condition(ItemCondition condition) {
        return condition == null ? null : "ItemCondition." + condition.name();
    }

    public static String tracking(ShipmentTrackingStatus status) {
        return status == null ? null : "order.shipment.tracking.status." + status.name();
    }

    public static String tone(ShipmentTrackingStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> NEUTRAL;
            case ACTIVE -> INFO;
            case FAILED -> BAD;
        };
    }

    /**
     * The pill of a shipment's courier cancellation, or null when there is none (a confirmed one clears the shipment).
     * A PENDING one past Shipment.STALE_CANCELLATION reads as unconfirmed: nothing answers it any more, and "Cancel
     * courier order" then re-checks it like an unconfirmed one.
     */
    public static String cancellation(Shipment shipment, LocalDateTime now) {
        if (shipment.getCancellationStatus() == null) {
            return null;
        }
        if (shipment.isCancellationInProgress(now)) {
            return "shipment.cancellation.pending";
        }
        return shipment.needsCancellationRecheck(now) ? "shipment.cancellation.unconfirmed" : "shipment.cancellation.failed";
    }

    public static String cancellationTone(Shipment shipment, LocalDateTime now) {
        if (shipment.getCancellationStatus() == null) {
            return null;
        }
        if (shipment.isCancellationInProgress(now)) {
            return INFO;
        }
        return shipment.needsCancellationRecheck(now) ? WARN : BAD;
    }

    /**
     * The message key of a failed cancellation's reason when it is the adapter's own English text, or null: every
     * other reason is Furgonetka's answer, already in Polish, and is shown as it came.
     */
    public static String cancellationReasonKey(String error) {
        return CANCEL_NOT_RECEIVED.equals(error) ? "shipment.cancellation.reason.notReceived" : null;
    }
}
