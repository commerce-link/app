package pl.commercelink.orders;

import java.time.LocalDate;

public class PastOrderFilter {
    private final String orderId;
    private final String email;
    private final LocalDate orderedAtStart;
    private final LocalDate orderedAtEnd;

    public PastOrderFilter(String orderId, String email, LocalDate orderedAtStart, LocalDate orderedAtEnd) {
        this.orderId = orderId;
        this.email = email;
        this.orderedAtStart = orderedAtStart;
        this.orderedAtEnd = orderedAtEnd;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getOrderedAtStart() {
        return orderedAtStart;
    }

    public LocalDate getOrderedAtEnd() {
        return orderedAtEnd;
    }
}