package pl.commercelink.orders.filters.exceptions;

public class OrderFilterConflictException extends OrderFilterException {

    public OrderFilterConflictException(String messageKey, Object... messageArguments) {
        super(messageKey, messageArguments);
    }
}
