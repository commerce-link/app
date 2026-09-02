package pl.commercelink.orders.filters;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDate;
import java.util.List;

public class OrderFilterMatcher {

    private final LocalDate today;

    public OrderFilterMatcher(LocalDate today) {
        this.today = today;
    }

    public boolean matches(Order order, OrderFilter filter) {
        if (filter == null || filter.getConditions() == null || filter.getConditions().isEmpty()) {
            return true;
        }
        return filter.getConditions().stream().allMatch(condition -> matches(order, condition));
    }

    public List<Order> apply(List<Order> orders, OrderFilter filter) {
        return orders.stream().filter(order -> matches(order, filter)).toList();
    }

    private boolean matches(Order order, OrderFilterCondition condition) {
        if (condition.getField() == null || condition.getValues() == null || condition.getValues().isEmpty()) {
            return true;
        }
        return condition.getValues().stream().anyMatch(value -> matches(order, condition.getField(), value));
    }

    private boolean matches(Order order, OrderFilterField field, String value) {
        return switch (field) {
            case Status -> matchesStatus(order, value);
            case ShipmentType -> matchesShipmentType(order, value);
            case PaymentSource -> matchesPaymentSource(order, value);
            case SourceName -> matchesSourceName(order, value);
            case ShippingPostalCode -> matchesPostalCode(order, value);
            case ShippingDue -> matchesShippingDue(order, value);
        };
    }

    private boolean matchesStatus(Order order, String value) {
        return order.getStatus() != null && order.getStatus().name().equalsIgnoreCase(value);
    }

    private boolean matchesShipmentType(Order order, String value) {
        ShipmentType type = parseEnum(ShipmentType.values(), value);
        if (type == null) {
            return false;
        }
        List<Shipment> shipments = order.getShipments();
        return shipments != null && shipments.stream().anyMatch(shipment -> shipment.getType() == type);
    }

    private boolean matchesPaymentSource(Order order, String value) {
        PaymentSource source = parseEnum(PaymentSource.values(), value);
        if (source == null) {
            return false;
        }
        List<Payment> payments = order.getPayments();
        return payments != null && payments.stream().anyMatch(payment -> payment.getSource() == source);
    }

    private boolean matchesSourceName(Order order, String value) {
        return order.getSource() != null
                && order.getSource().getName() != null
                && order.getSource().getName().equalsIgnoreCase(value);
    }

    private boolean matchesPostalCode(Order order, String value) {
        if (order.getShippingDetails() == null) {
            return false;
        }
        String postalCode = normalize(order.getShippingDetails().getPostalCode());
        String prefix = normalize(value);
        return !prefix.isEmpty() && postalCode.startsWith(prefix);
    }

    private boolean matchesShippingDue(Order order, String value) {
        ShippingDue due = ShippingDue.parse(value).orElse(null);
        if (due == null) {
            return false;
        }
        LocalDate estimatedShippingAt = order.getEstimatedShippingAt();
        return switch (due) {
            case DueToday -> estimatedShippingAt != null && !estimatedShippingAt.isAfter(today);
            case Overdue -> estimatedShippingAt != null && estimatedShippingAt.isBefore(today);
            case Unscheduled -> estimatedShippingAt == null;
        };
    }

    private static <E extends Enum<E>> E parseEnum(E[] values, String value) {
        for (E candidate : values) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
