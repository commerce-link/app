package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

class OrderReviewEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("orderStatusLink")
    private String orderStatusLink;

    OrderReviewEmailNotification(String recipientEmail, String recipientName, String orderId, String orderStatusLink) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.orderStatusLink = orderStatusLink;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderStatusLink() {
        return orderStatusLink;
    }
}
