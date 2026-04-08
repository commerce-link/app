package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

class RMARejectedEmailNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("rejectionReason")
    private String rejectionReason;
    @JsonProperty("rmaClientLink")
    private String rmaClientLink;

    RMARejectedEmailNotification(String recipientEmail, String recipientName, String rmaId, String orderId, String rejectionReason, String rmaClientLink) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.rejectionReason = rejectionReason;
        this.rmaClientLink = rmaClientLink;
    }

    public String getRmaId() {
        return rmaId;
    }

    public void setRmaId(String rmaId) {
        this.rmaId = rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRmaClientLink() {
        return rmaClientLink;
    }

    public void setRmaClientLink(String rmaClientLink) {
        this.rmaClientLink = rmaClientLink;
    }
}
