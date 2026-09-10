package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.time.LocalDate;

public class OrderPickupEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("orderStatusLink")
    private String orderStatusLink;
    @JsonProperty("estimatedCollectionDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate estimatedCollectionDate;

    public OrderPickupEmailNotification(String recipientEmail, String recipientName, String orderId, String orderStatusLink, LocalDate estimatedCollectionDate) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.orderStatusLink = orderStatusLink;
        this.estimatedCollectionDate = estimatedCollectionDate;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderStatusLink() {
        return orderStatusLink;
    }

    public LocalDate getEstimatedCollectionDate() {
        return estimatedCollectionDate;
    }
}
