package pl.commercelink.checkout;

public class CheckoutException extends RuntimeException {

    private final CheckoutError error;
    private final Object[] messageArgs;

    public CheckoutException(CheckoutError error, String detail, Object... messageArgs) {
        super(error.apiCode() + ": " + detail);
        this.error = error;
        this.messageArgs = messageArgs;
    }

    public CheckoutException(CheckoutError error, String detail, Throwable cause) {
        super(error.apiCode() + ": " + detail, cause);
        this.error = error;
        this.messageArgs = new Object[0];
    }

    public CheckoutError getError() {
        return error;
    }

    public Object[] getMessageArgs() {
        return messageArgs.clone();
    }
}
