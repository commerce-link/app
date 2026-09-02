package pl.commercelink.orders.filters;

import java.util.Arrays;
import java.util.Optional;

public enum OrderFilterField {

    Status,
    ShipmentType,
    PaymentSource,
    SourceName,
    ShippingPostalCode,
    ShippingDue;

    public static Optional<OrderFilterField> parse(String value) {
        return Arrays.stream(values())
                .filter(field -> field.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
