package pl.commercelink.orders.filters;

public class OrderFilterInvalidException extends RuntimeException {

    public OrderFilterInvalidException(String message) {
        super(message);
    }
}
