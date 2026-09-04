package pl.commercelink.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** Return-specific part of an {@link OrderLifecycleEvent}; null for plain order events. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketplaceReturnAction {

    private String rmaId;
    private String externalReturnId;
    private List<Item> items = new ArrayList<>();
    private boolean refundDelivery;
    /** Idempotency key for the marketplace refund; generated once, stable across SQS redeliveries. */
    private String commandId;
    private String rejectionReason;
    /** The return's buyer-facing reference number (e.g. "XGQX/2026"); null when unavailable. */
    private String externalReturnReference;

    public MarketplaceReturnAction() {
    }

    public MarketplaceReturnAction(String rmaId, String externalReturnId, List<Item> items, boolean refundDelivery,
                                   String commandId, String rejectionReason) {
        this.rmaId = rmaId;
        this.externalReturnId = externalReturnId;
        this.items = items;
        this.refundDelivery = refundDelivery;
        this.commandId = commandId;
        this.rejectionReason = rejectionReason;
    }

    public String getRmaId() { return rmaId; }
    public void setRmaId(String rmaId) { this.rmaId = rmaId; }
    public String getExternalReturnId() { return externalReturnId; }
    public void setExternalReturnId(String externalReturnId) { this.externalReturnId = externalReturnId; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public boolean isRefundDelivery() { return refundDelivery; }
    public void setRefundDelivery(boolean refundDelivery) { this.refundDelivery = refundDelivery; }
    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getExternalReturnReference() { return externalReturnReference; }
    public void setExternalReturnReference(String externalReturnReference) { this.externalReturnReference = externalReturnReference; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String manufacturerCode;
        private int quantity;

        public Item() {
        }

        public Item(String manufacturerCode, int quantity) {
            this.manufacturerCode = manufacturerCode;
            this.quantity = quantity;
        }

        public String getManufacturerCode() { return manufacturerCode; }
        public void setManufacturerCode(String manufacturerCode) { this.manufacturerCode = manufacturerCode; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
