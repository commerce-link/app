package pl.commercelink.receipts;

/** An operator action on receipts was refused; the message key explains why. */
public class ReceiptActionException extends RuntimeException {

    private final String messageKey;

    public ReceiptActionException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
