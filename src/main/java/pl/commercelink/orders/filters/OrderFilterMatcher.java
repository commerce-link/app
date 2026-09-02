package pl.commercelink.orders.filters;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;

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
        return filter.getConditions().stream().allMatch(entry -> matches(order, entry));
    }

    public List<Order> apply(List<Order> orders, OrderFilter filter) {
        return orders.stream().filter(order -> matches(order, filter)).toList();
    }

    private boolean matches(Order order, String entry) {
        OrderFilterField field = OrderFilterConditions.fieldOf(entry).orElse(null);
        if (field == null) {
            return false;
        }
        String value = OrderFilterConditions.valueOf(entry);

        return switch (field) {
            case Status -> matchesStatus(order, field, value);
            case ShipmentType -> matchesShipmentType(order, field, value);
            case PaymentSource -> matchesPaymentSource(order, field, value);
            case SourceName -> matchesSourceName(order, field, value);
            case ShippingPostalCode -> matchesPostalCode(order, field, value);
            case ShippingDue -> matchesShippingDue(order, value);
        };
    }

    private boolean matchesStatus(Order order, OrderFilterField field, String value) {
        return order.getStatus() != null && field.normalize(order.getStatus().name()).equals(value);
    }

    private boolean matchesShipmentType(Order order, OrderFilterField field, String value) {
        List<Shipment> shipments = order.getShipments();
        if (shipments == null) {
            return false;
        }
        return shipments.stream()
                .map(Shipment::getType)
                .filter(type -> type != null)
                .anyMatch(type -> field.normalize(type.name()).equals(value));
    }

    private boolean matchesPaymentSource(Order order, OrderFilterField field, String value) {
        List<Payment> payments = order.getPayments();
        if (payments == null) {
            return false;
        }
        return payments.stream()
                .map(Payment::getSource)
                .filter(source -> source != null)
                .anyMatch(source -> field.normalize(source.name()).equals(value));
    }

    private boolean matchesSourceName(Order order, OrderFilterField field, String value) {
        return order.getSource() != null
                && order.getSource().getName() != null
                && field.normalize(order.getSource().getName()).equals(value);
    }

    private boolean matchesPostalCode(Order order, OrderFilterField field, String value) {
        if (order.getShippingDetails() == null) {
            return false;
        }
        return field.normalize(order.getShippingDetails().getPostalCode()).startsWith(value);
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
}
