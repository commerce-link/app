package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.starter.localization.EnumLocalizer;

class RMACarrierArrangeEmailNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("status")
    private String status;
    @JsonProperty("rmaClientLink")
    private String rmaClientLink;

    RMACarrierArrangeEmailNotification(String recipientEmail, String recipientName, String rmaId, String orderId, RMAStatus status, String rmaClientLink, EnumLocalizer enumLocalizer) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.status = enumLocalizer.localize(status);
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
