package pl.commercelink.web.orders;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.warehouse.api.ItemCondition;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Message keys and pill tones of the enums shown on the order screens: templates read enum text and tone only through here. */
public final class OrderLabels {

    public static final String OK = "is-ok";
    public static final String WARN = "is-warn";
    public static final String INFO = "is-info";
    public static final String BAD = "is-bad";
    public static final String NEUTRAL = "is-neutral";

    private static final Object[] NO_ARGS = new Object[0];

    /**
     * Our own failure sentences that already state the outcome (not confirmed, not ordered): shown alone, since
     * "Nie udało się nadać: Furgonetka nie potwierdziła nadania" would claim a failure nobody knows of, and "Nie udało
     * się zamówić odbioru: Odbiór nie został zamówiony" says it twice. Every other stored key names a cause and is put
     * after the failure prefix, as the provider's own words are.
     */
    private static final Set<String> OUTCOME_KEYS = Set.of(ShipmentCreationState.UNCONFIRMED_KEY,
            ShipmentPickup.UNCONFIRMED_KEY, "shipping.pickup.not.sent", "shipping.pickup.no.provider");

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

    /**
     * The state of an order item. Delivered has its own label ("Skompletowany": the goods are in the store's hands),
     * since FulfilmentStatus.Delivered is also the warehouse stock state, which the warehouse screens localise through
     * the enum's own key ("Dostarczony": in stock).
     */
    public static String itemStatus(FulfilmentStatus status) {
        if (status == null) {
            return null;
        }
        return status == FulfilmentStatus.Delivered ? "order.item.status.Delivered" : "FulfilmentStatus." + status.name();
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

    /**
     * The word before a document's number in the documents card, which the number itself already qualifies
     * ("FV/2026/09/118", "WZ/MAG/2026/000001"): every invoice is "Faktura", the receipt "Paragon", a warehouse
     * document "Dokument". A pro forma and an order confirmation keep their own names: neither is an invoice.
     */
    public static String documentPrefix(DocumentType type) {
        if (type == null || type.isWarehouseDocument()) {
            return "order.documents.prefix.document";
        }
        return switch (type) {
            case Receipt -> "order.documents.prefix.receipt";
            case InvoiceVat, InvoiceAdvance, InvoiceFinal, InvoicePersonal -> "order.documents.prefix.invoice";
            default -> documentType(type);
        };
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
     * A PENDING one past CourierCancellation.STALE reads as unconfirmed: nothing answers it any more, and "Cancel
     * courier order" then re-checks it like an unconfirmed one.
     */
    public static String cancellation(Shipment shipment, LocalDateTime now) {
        if (shipment.getCancellation() == null) {
            return null;
        }
        if (shipment.isCancellationInProgress(now)) {
            return "shipment.cancellation.pending";
        }
        return shipment.needsCancellationRecheck(now) ? "shipment.cancellation.unconfirmed" : "shipment.cancellation.failed";
    }

    public static String cancellationTone(Shipment shipment, LocalDateTime now) {
        if (shipment.getCancellation() == null) {
            return null;
        }
        if (shipment.isCancellationInProgress(now)) {
            return INFO;
        }
        return shipment.needsCancellationRecheck(now) ? WARN : BAD;
    }

    /**
     * The message key of an immediate cancellation failure's reason (the flash after the click) when it is the
     * adapter's own English text, or null: every other reason is Furgonetka's answer, already in Polish, and is shown
     * as it came.
     */
    public static String cancellationReasonKey(String error) {
        return CANCEL_NOT_RECEIVED.equals(error) ? "shipment.cancellation.reason.notReceived" : null;
    }

    /**
     * The line under a shipment created through an integration: being created, failed, waiting for a pickup, pickup
     * being ordered, ordered (by us, or by the carrier with the shipment), handed in at a point, pickup failed; null
     * for one typed in by hand. The provider's words (error) are the argument of the failure line, shown as they came;
     * a stored reason of our own (errorKey) is resolved in the viewer's language, as that argument when it names a
     * cause, or as the line itself when it already states the outcome (OUTCOME_KEYS).
     */
    public static ShipmentState shipmentState(Shipment shipment, Locale locale) {
        if (shipment.isCreating()) {
            return new ShipmentState("order.shipments.state.creating", NO_ARGS, INFO, true);
        }
        if (shipment.creationFailed()) {
            ShipmentCreationState creation = shipment.getCreation();
            return failure("order.shipments.state.creation.failed", creation.getCommand().getError(),
                    creation.isPending() ? ShipmentCreationState.UNCONFIRMED_KEY : creation.getCommand().getErrorKey());
        }
        ShipmentPickup pickup = shipment.getPickup();
        if (shipment.getProvider() == null || pickup == null || pickup.getStatus() == null) {
            return null;
        }
        // a delivered package was collected whatever its pickup state says: no "Czeka na odbiór" next to the delivery
        if (shipment.getDeliveredAt() != null && pickup.isAwaiting()) {
            return null;
        }
        return switch (pickup.getStatus()) {
            case AWAITING -> new ShipmentState("order.shipments.state.pickup.awaiting", NO_ARGS, NEUTRAL, false);
            case PENDING -> pickup.isUnconfirmed(LocalDateTime.now())
                    ? failure("order.shipments.state.pickup.failed", null, ShipmentPickup.UNCONFIRMED_KEY)
                    : new ShipmentState("order.shipments.state.pickup.pending", NO_ARGS, INFO, true);
            case ORDERED -> pickup.isBookedByCarrier()
                    ? new ShipmentState("order.shipments.state.pickup.carrier", new Object[]{pickup.getPickupId()}, OK,
                    false)
                    : new ShipmentState("order.shipments.state.pickup.ordered", new Object[]{
                    pickup.getWindow().formatDay(locale), pickup.getWindow().formatFrom(), pickup.getWindow().formatTo()},
                    OK, false);
            case NOT_REQUIRED -> new ShipmentState("order.shipments.state.pickup.point", NO_ARGS, NEUTRAL, false);
            case FAILED -> failure("order.shipments.state.pickup.failed", pickup.getCommand().getError(),
                    pickup.getCommand().getErrorKey());
        };
    }

    private static ShipmentState failure(String key, String error, String errorKey) {
        if (errorKey != null && OUTCOME_KEYS.contains(errorKey)) {
            return new ShipmentState(errorKey, NO_ARGS, WARN, false);
        }
        // the message source resolves a resolvable argument in the locale of the line itself
        Object reason = errorKey != null ? new DefaultMessageSourceResolvable(errorKey) : error == null ? "" : error;
        return new ShipmentState(key, new Object[]{reason}, WARN, false);
    }

    /**
     * key with args: the message of the line (render with #messages.msgWithParams: a message expression would pass
     * the array as one argument). inProgress: the line waits for the provider, shown with a spinner.
     */
    public record ShipmentState(String key, Object[] args, String tone, boolean inProgress) {
    }
}
