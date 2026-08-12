package pl.commercelink.orders.rma;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class RMAFilter {
    private final String rmaId;
    private final String orderId;
    private final String email;
    private final LocalDate createdAtStart;
    private final LocalDate createdAtEnd;
    private final List<RMAStatus> statuses;

    public RMAFilter(String rmaId, String orderId, String email, LocalDate createdAtStart, LocalDate createdAtEnd) {
        this(rmaId, orderId, email, createdAtStart, createdAtEnd, null);
    }

    public RMAFilter(String rmaId, String orderId, String email, LocalDate createdAtStart, LocalDate createdAtEnd, List<RMAStatus> statuses) {
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.email = email;
        this.createdAtStart = createdAtStart;
        this.createdAtEnd = createdAtEnd;
        this.statuses = statuses != null ? statuses : Collections.emptyList();
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getCreatedAtStart() {
        return createdAtStart;
    }

    public LocalDate getCreatedAtEnd() {
        return createdAtEnd;
    }

    public List<RMAStatus> getStatuses() {
        return statuses;
    }

    public boolean hasStatuses() {
        return !statuses.isEmpty();
    }

    public boolean hasAnyFilter() {
        return (rmaId != null && !rmaId.isEmpty()) ||
               (orderId != null && !orderId.isEmpty()) ||
               (email != null && !email.isEmpty()) ||
               createdAtStart != null ||
               createdAtEnd != null ||
               hasStatuses();
    }

    @Override
    public String toString() {
        return "RMAFilter{" +
                "rmaId='" + rmaId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", email='" + email + '\'' +
                ", createdAtStart=" + createdAtStart +
                ", createdAtEnd=" + createdAtEnd +
                ", statuses=" + statuses +
                '}';
    }
}
