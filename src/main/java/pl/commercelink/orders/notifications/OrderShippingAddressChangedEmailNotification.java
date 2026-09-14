package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.email.EmailNotification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class OrderShippingAddressChangedEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private final String orderId;
    @JsonProperty("orderStatusLink")
    private final String orderStatusLink;
    @JsonProperty("previousShippingDetails")
    private final ShippingDetails previousShippingDetails;
    @JsonProperty("newShippingDetails")
    private final ShippingDetails newShippingDetails;
    @JsonProperty("changedAt")
    private final String changedAt;
    @JsonProperty("contactEmail")
    private final String contactEmail;

    public OrderShippingAddressChangedEmailNotification(String recipientEmail, String recipientName, String orderId,
                                                        String orderStatusLink, ShippingDetails previousShippingDetails,
                                                        ShippingDetails newShippingDetails, LocalDateTime changedAt,
                                                        String contactEmail) {
        super(recipientEmail, recipientName);
        this.orderId = orderId;
        this.orderStatusLink = orderStatusLink;
        this.previousShippingDetails = previousShippingDetails;
        this.newShippingDetails = newShippingDetails;
        this.changedAt = changedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        this.contactEmail = contactEmail;
    }
}
