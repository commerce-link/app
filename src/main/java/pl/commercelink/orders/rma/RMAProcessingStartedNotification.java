package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

public class RMAProcessingStartedNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;

    RMAProcessingStartedNotification(String recipientEmail, String recipientName, String rmaId, String orderId) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }
}
