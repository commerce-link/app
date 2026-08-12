package pl.commercelink.orders.rma;

public enum RMAItemStatus {
    New,
    Received,
    SentForRepair,
    ReturnedToClient,
    MovedToWarehouse;

    public String tagClass() {
        return switch (this) {
            case New -> "is-info";
            case Received -> "is-link";
            case SentForRepair -> "is-warning";
            case ReturnedToClient -> "is-success";
            case MovedToWarehouse -> "is-primary";
        };
    }
}
