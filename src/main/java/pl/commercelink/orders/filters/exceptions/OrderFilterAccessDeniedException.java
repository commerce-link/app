package pl.commercelink.orders.filters.exceptions;

public class OrderFilterAccessDeniedException extends RuntimeException {

    public OrderFilterAccessDeniedException(String message) {
        super(message);
    }
}
