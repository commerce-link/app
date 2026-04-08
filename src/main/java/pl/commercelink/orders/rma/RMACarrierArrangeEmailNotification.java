package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.util.Locale;

class RMACarrierArrangeEmailNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("status")
    private String status;
    @JsonProperty("rmaClientLink")
    private String rmaClientLink;

    RMACarrierArrangeEmailNotification(String recipientEmail, String recipientName, String rmaId, String orderId, RMAStatus status, String rmaClientLink) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.status = status.getLocalizedName(new Locale("pl"));
        this.rmaClientLink = rmaClientLink;
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getRmaClientLink() {
        return rmaClientLink;
    }
}
