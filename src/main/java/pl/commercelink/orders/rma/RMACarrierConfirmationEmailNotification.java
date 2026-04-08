package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedList;
import java.util.List;

class RMACarrierConfirmationEmailNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("shippingDetails")
    private ShippingDetails shippingDetails;
    @JsonProperty("trackingUrls")
    private List<String> trackingUrls = new LinkedList<>();

    RMACarrierConfirmationEmailNotification(String recipientEmail, String recipientName, String rmaId, String orderId, ShippingDetails shippingDetails) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.shippingDetails = shippingDetails;
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public List<String> getTrackingUrls() {
        return trackingUrls;
    }

    public void addTrackingUrl(String trackingUrl) {
        this.trackingUrls.add(trackingUrl);
    }
}
