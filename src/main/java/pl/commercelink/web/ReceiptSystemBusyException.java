package pl.commercelink.web;

/** Switching or disconnecting the store's e-receipt system was refused because an attempt still needs it. */
public class ReceiptSystemBusyException extends RuntimeException {

    public ReceiptSystemBusyException(String message) {
        super(message);
    }
}
