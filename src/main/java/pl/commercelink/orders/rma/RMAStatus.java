package pl.commercelink.orders.rma;

public enum RMAStatus {
    New,
    Approved,
    Rejected,
    WaitingForItems,
    ItemsReceived,
    Processing,
    Completed;

    public boolean isClosed() {
        return this == Rejected || this == Completed;
    }

    public String tagClass() {
        return switch (this) {
            case New -> "is-info";
            case Approved -> "is-primary";
            case Rejected -> "is-danger";
            case WaitingForItems -> "is-warning";
            case ItemsReceived -> "is-link";
            case Processing -> "is-warning is-light";
            case Completed -> "is-success";
        };
    }
}
