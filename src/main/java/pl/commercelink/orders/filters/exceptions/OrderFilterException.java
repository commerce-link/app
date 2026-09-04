package pl.commercelink.orders.filters.exceptions;

public abstract class OrderFilterException extends RuntimeException {

    private final transient Object[] messageArguments;

    protected OrderFilterException(String messageKey, Object... messageArguments) {
        super(messageKey);
        this.messageArguments = messageArguments;
    }

    public String getMessageKey() {
        return getMessage();
    }

    public Object[] getMessageArguments() {
        return messageArguments;
    }
}
