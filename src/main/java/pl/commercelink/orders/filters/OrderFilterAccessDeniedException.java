package pl.commercelink.orders.filters;

public class OrderFilterAccessDeniedException extends RuntimeException {

    public OrderFilterAccessDeniedException(String message) {
        super(message);
    }
}
