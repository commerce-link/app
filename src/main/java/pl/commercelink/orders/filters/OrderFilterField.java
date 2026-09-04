package pl.commercelink.orders.filters;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public enum OrderFilterField {

    Status {
        @Override
        boolean matches(Order order, String value, LocalDate today) {
            return order.getStatus() != null && normalize(order.getStatus().name()).equals(value);
        }
    },

    ShipmentType {
        @Override
        boolean matches(Order order, String value, LocalDate today) {
            List<Shipment> shipments = order.getShipments();
            return shipments != null && shipments.stream()
                    .map(Shipment::getType)
                    .filter(Objects::nonNull)
                    .anyMatch(type -> normalize(type.name()).equals(value));
        }
    },

    PaymentSource {
        @Override
        boolean matches(Order order, String value, LocalDate today) {
            List<Payment> payments = order.getPayments();
            return payments != null && payments.stream()
                    .map(Payment::getSource)
                    .filter(Objects::nonNull)
                    .anyMatch(source -> normalize(source.name()).equals(value));
        }
    },

    SourceName {
        @Override
        boolean matches(Order order, String value, LocalDate today) {
            return order.getSource() != null
                    && order.getSource().getName() != null
                    && normalize(order.getSource().getName()).equals(value);
        }
    },

    ShippingPostalCode {
        @Override
        public String normalize(String value) {
            return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        }

        @Override
        boolean matches(Order order, String value, LocalDate today) {
            return order.getShippingDetails() != null
                    && normalize(order.getShippingDetails().getPostalCode()).startsWith(value);
        }
    },

    ShippingDue {
        @Override
        boolean matches(Order order, String value, LocalDate today) {
            return pl.commercelink.orders.filters.ShippingDue.parse(value)
                    .map(due -> due.covers(order.getEstimatedShippingAt(), today))
                    .orElse(false);
        }
    };

    public String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    abstract boolean matches(Order order, String value, LocalDate today);

    public static Optional<OrderFilterField> parse(String value) {
        return Arrays.stream(values())
                .filter(field -> field.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
