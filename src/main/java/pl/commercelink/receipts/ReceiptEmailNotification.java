package pl.commercelink.receipts;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

/** The e-receipt link for the buyer; the receipt itself lives at the provider, so there is no attachment. */
public class ReceiptEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private final String orderId;
    @JsonProperty("receiptUrl")
    private final String receiptUrl;

    public ReceiptEmailNotification(String recipientEmail, String recipientName, String orderId, String receiptUrl) {
        super(recipientEmail, recipientName);
        this.orderId = orderId;
        this.receiptUrl = receiptUrl;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }
}
