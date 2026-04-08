package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.time.LocalDate;

class OrderRealizationEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("estimatedShippingDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate estimatedShippingDate;

    OrderRealizationEmailNotification(String recipientEmail, String recipientName, String orderId, LocalDate estimatedShippingDate) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.estimatedShippingDate = estimatedShippingDate;
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDate getEstimatedShippingDate() {
        return estimatedShippingDate;
    }
}
