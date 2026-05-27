package pl.commercelink.orders;

public enum OrderStatus {
    New,
    Blocked,
    Assembly,
    Assembled,
    Realization,
    Shipping,
    Delivered,
    Cancelled,
    Completed;

    public boolean isOneOf(OrderStatus... others) {
        for (OrderStatus other : others) {
            if (this == other) {
                return true;
            }
        }
        return false;
    }
}
