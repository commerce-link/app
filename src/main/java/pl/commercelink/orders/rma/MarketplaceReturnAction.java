package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Body of a {@link ReturnLifecycleEvent}: what to refund or why the return was rejected. Stored verbatim
 * (as part of the event JSON) in {@code RMA.marketplaceDecisions} so a decision can be republished with the
 * same commandId.
 *
 * @param commandId idempotency key for the marketplace refund; generated once, stable across SQS redeliveries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceReturnAction(
        String rmaId,
        String externalReturnId,
        List<Item> items,
        boolean refundDelivery,
        String commandId,
        String rejectionReason
) {

    public MarketplaceReturnAction {
        items = items == null ? List.of() : items;
    }

    /** @param marketplaceKey the order item's marketplace key (externalItemId, else sku, else manufacturerCode). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String marketplaceKey, int quantity) {
    }
}
