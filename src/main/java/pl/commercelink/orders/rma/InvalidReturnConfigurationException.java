package pl.commercelink.orders.rma;

public class InvalidReturnConfigurationException extends RuntimeException {

    public InvalidReturnConfigurationException(String message) {
        super(message);
    }

    public InvalidReturnConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
