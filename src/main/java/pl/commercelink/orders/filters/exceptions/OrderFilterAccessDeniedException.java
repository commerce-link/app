package pl.commercelink.orders.filters.exceptions;

public class OrderFilterAccessDeniedException extends OrderFilterException {

    public OrderFilterAccessDeniedException(String messageKey, Object... messageArguments) {
        super(messageKey, messageArguments);
    }
}
