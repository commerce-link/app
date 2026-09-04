package pl.commercelink.orders.filters.exceptions;

public class OrderFilterInvalidException extends OrderFilterException {

    public OrderFilterInvalidException(String messageKey, Object... messageArguments) {
        super(messageKey, messageArguments);
    }
}
