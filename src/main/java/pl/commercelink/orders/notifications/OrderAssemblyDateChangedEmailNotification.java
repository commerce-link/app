package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.time.LocalDate;

class OrderAssemblyDateChangedEmailNotification extends EmailNotification {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("orderStatusLink")
    private String orderStatusLink;
    @JsonProperty("oldAssemblyDate")
    private LocalDate oldAssemblyDate;
    @JsonProperty("newAssemblyDate")
    private LocalDate newAssemblyDate;

    OrderAssemblyDateChangedEmailNotification(String recipientEmail, String recipientName, String orderId, String orderStatusLink, LocalDate oldAssemblyDate, LocalDate newAssemblyDate) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.orderStatusLink = orderStatusLink;
        this.oldAssemblyDate = oldAssemblyDate;
        this.newAssemblyDate = newAssemblyDate;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderStatusLink() {
        return orderStatusLink;
    }

    public LocalDate getOldAssemblyDate() {
        return oldAssemblyDate;
    }

    public LocalDate getNewAssemblyDate() {
        return newAssemblyDate;
    }
}
