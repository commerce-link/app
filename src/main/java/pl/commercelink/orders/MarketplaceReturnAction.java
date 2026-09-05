package pl.commercelink.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Return-specific part of an {@link OrderLifecycleEvent}; null for plain order events. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
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

    public MarketplaceReturnAction(String rmaId, String externalReturnId, List<Item> items, boolean refundDelivery,
                                   String commandId, String rejectionReason) {
        this.rmaId = rmaId;
        this.externalReturnId = externalReturnId;
        this.items = items;
        this.refundDelivery = refundDelivery;
        this.commandId = commandId;
        this.rejectionReason = rejectionReason;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String manufacturerCode;
        private int quantity;
    }
}
