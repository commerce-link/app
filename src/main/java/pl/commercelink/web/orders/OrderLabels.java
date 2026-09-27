package pl.commercelink.web.orders;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.warehouse.api.ItemCondition;

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
}
