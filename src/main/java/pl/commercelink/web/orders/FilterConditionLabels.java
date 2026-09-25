package pl.commercelink.web.orders;

import org.thymeleaf.expression.Messages;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.ShippingDue;
import pl.commercelink.orders.filters.model.OrderFilterCondition;

import java.util.Arrays;
import java.util.function.Function;

/**
 * The small lookups a filter condition needs to print itself, shared by the filter dialogs' template
 * (orders/filters.html) and by OrderListService.saveViewConditions: the tail of a field's message key
 * ("orders.filters.field." + fieldKey), the form parameter a field is posted under, and a condition's
 * human display value.
 */
public final class FilterConditionLabels {

    private FilterConditionLabels() {
    }

    public static String fieldKey(OrderFilterField field) {
        return switch (field) {
            case Status -> "status";
            case ShipmentType -> "shipment.type";
            case PaymentSource -> "payment.source";
            case ShippingDue -> "shipping.due";
            case SourceName -> "marketplace";
            case ShippingPostalCode -> "postal.code";
        };
    }

    public static String formField(String fieldName) {
        OrderFilterField field = OrderFilterField.parse(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order filter field: " + fieldName));
        return switch (field) {
            case Status -> "status";
            case ShipmentType -> "shipmentType";
            case PaymentSource -> "paymentSource";
            case ShippingDue -> "shippingDue";
            case SourceName -> "sourceName";
            case ShippingPostalCode -> "shippingPostalCode";
        };
    }

    /** The display value of a condition, for a Thymeleaf template holding an org.thymeleaf.expression.Messages. */
    public static String value(OrderFilterCondition condition, Messages messages) {
        return value(condition, messages::msg);
    }

    /** The display value of a condition: the enum's human label for Status/ShipmentType/PaymentSource/ShippingDue
     * (resolved case-insensitively, since stored values may differ in case from the enum name), the raw value
     * otherwise. */
    public static String value(OrderFilterCondition condition, Function<String, String> lookup) {
        String raw = condition.getValue();
        return switch (condition.getField()) {
            case Status -> enumLabel(OrderStatus.class, raw, "OrderStatus.", lookup);
            case ShipmentType -> enumLabel(ShipmentType.class, raw, "ShipmentType.", lookup);
            case PaymentSource -> enumLabel(PaymentSource.class, raw, "PaymentSource.", lookup);
            case ShippingDue -> enumLabel(ShippingDue.class, raw, "ShippingDue.", lookup);
            default -> raw;
        };
    }

    private static <E extends Enum<E>> String enumLabel(Class<E> type, String rawValue, String keyPrefix, Function<String, String> lookup) {
        return Arrays.stream(type.getEnumConstants())
                .filter(v -> v.name().equalsIgnoreCase(rawValue))
                .findFirst()
                .map(v -> lookup.apply(keyPrefix + v.name()))
                .orElse(rawValue);
    }
}
