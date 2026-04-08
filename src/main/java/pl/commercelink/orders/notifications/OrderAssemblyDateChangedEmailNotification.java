package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.time.LocalDate;

class OrderAssemblyDateChangedEmailNotification extends EmailNotification {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("oldAssemblyDate")
    private LocalDate oldAssemblyDate;
    @JsonProperty("newAssemblyDate")
    private LocalDate newAssemblyDate;

    OrderAssemblyDateChangedEmailNotification(String recipientEmail, String recipientName, String orderId, LocalDate oldAssemblyDate, LocalDate newAssemblyDate) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.oldAssemblyDate = oldAssemblyDate;
        this.newAssemblyDate = newAssemblyDate;
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDate getOldAssemblyDate() {
        return oldAssemblyDate;
    }

    public LocalDate getNewAssemblyDate() {
        return newAssemblyDate;
    }
}
