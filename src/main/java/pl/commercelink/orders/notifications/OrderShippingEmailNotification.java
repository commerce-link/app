package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.util.LinkedList;
import java.util.List;

class OrderShippingEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("isReceipt")
    private boolean isReceipt;
    @JsonProperty("isInvoice")
    private boolean isInvoice;
    @JsonProperty("trackingUrls")
    private List<String> trackingUrls = new LinkedList<>();

    public OrderShippingEmailNotification(String recipientEmail, String recipientName, String orderId, boolean isReceipt, boolean isInvoice) {
        super(recipientEmail, recipientName);

        this.orderId = orderId;
        this.isReceipt = isReceipt;
        this.isInvoice = isInvoice;
    }

    public void addTrackingUrl(String trackingUrl) {
        this.trackingUrls.add(trackingUrl);
    }

    public String getOrderId() {
        return orderId;
    }

    public boolean isReceipt() {
        return isReceipt;
    }

    public boolean isInvoice() {
        return isInvoice;
    }

    public List<String> getTrackingUrls() {
        return trackingUrls;
    }
}
