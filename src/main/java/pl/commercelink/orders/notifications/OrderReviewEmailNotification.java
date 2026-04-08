package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

class OrderReviewEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;

    OrderReviewEmailNotification(String recipientEmail, String recipientName, String orderId) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;    }

    public String getOrderId() {
        return orderId;
    }
}
