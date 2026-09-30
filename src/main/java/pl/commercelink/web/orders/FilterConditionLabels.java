package pl.commercelink.web.orders;

import org.thymeleaf.expression.Messages;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.ShippingDue;
import pl.commercelink.orders.filters.model.OrderFilterCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The small lookups a filter condition needs to print itself, shared by the filter dialogs' template
 * (orders/filters.html): the tail of a field's message key ("orders.filters.field." + fieldKey), the form parameter
 * a field is posted under, a condition's human display value, one pill per field on the filters page with several
 * values, and a field's closed menu trigger label.
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

    /** "Marketplace: Allegro, Ceneo" — one pill per field on the filters page. */
    public static String pill(String fieldName, List<String> values, Messages messages) {
        return pill(fieldName, values, messages::msgWithParams);
    }

    public static String pill(String fieldName, List<String> values, BiFunction<String, Object[], String> lookup) {
        OrderFilterField field = OrderFilterField.valueOf(fieldName);
        return lookup.apply("orders.filters.field." + fieldKey(field), new Object[0]) + ": " + labels(field, values, lookup);
    }

    /** What a multi-value field's closed menu says: "Dowolny", its one value, or "Wybrano: N". */
    public static String summary(String fieldName, List<String> values, Messages messages) {
        return summary(fieldName, values, messages::msgWithParams);
    }

    public static String summary(String fieldName, List<String> values, BiFunction<String, Object[], String> lookup) {
        if (values == null || values.isEmpty()) {
            return lookup.apply("orders.filters.any.value", new Object[0]);
        }
        if (values.size() == 1) {
            return labels(OrderFilterField.valueOf(fieldName), values, lookup);
        }
        return lookup.apply("orders.filters.selected", new Object[]{values.size()});
    }

    /** Stored values may differ in case from the option (see {@link #value}). */
    public static boolean isPicked(List<String> values, String option) {
        return values != null && values.stream().anyMatch(option::equalsIgnoreCase);
    }

    /**
     * What a field's menu lists: the offered options, then any stored value that is no longer offered (a marketplace
     * disconnected since, a closed status), so saving the form keeps it unless the operator unticks it on purpose.
     */
    public static List<String> options(List<String> offered, List<String> picked) {
        List<String> options = new ArrayList<>(offered);
        if (picked != null) {
            picked.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> options.stream().noneMatch(value::equalsIgnoreCase))
                    .forEach(options::add);
        }
        return options;
    }

    /** A menu option's label; a stored value that is no known constant of the field shows as stored. */
    public static String optionLabel(String fieldName, String option, Messages messages) {
        return value(OrderFilterCondition.of(OrderFilterField.valueOf(fieldName), option), messages::msg);
    }

    private static String labels(OrderFilterField field, List<String> values, BiFunction<String, Object[], String> lookup) {
        return values.stream()
                .map(raw -> value(OrderFilterCondition.of(field, raw), key -> lookup.apply(key, new Object[0])))
                .collect(Collectors.joining(", "));
    }
}
