package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.time.LocalDate;

class OrderAssemblyEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("estimatedAssemblyDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate estimatedAssemblyDate;
    @JsonProperty("estimatedShippingDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate estimatedShippingDate;
    @JsonProperty("personalCollection")
    private boolean personalCollection;

    OrderAssemblyEmailNotification(String recipientEmail, String recipientName, String orderId, LocalDate estimatedAssemblyDate, LocalDate estimatedShippingDate, boolean personalCollection) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.estimatedAssemblyDate = estimatedAssemblyDate;
        this.estimatedShippingDate = estimatedShippingDate;
        this.personalCollection = personalCollection;
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDate getEstimatedAssemblyDate() {
        return estimatedAssemblyDate;
    }

    public LocalDate getEstimatedShippingDate() {
        return estimatedShippingDate;
    }

    public boolean isPersonalCollection() {
        return personalCollection;
    }
}
