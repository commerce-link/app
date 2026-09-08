package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pl.commercelink.orders.MarketplaceReturnAction;

/**
 * A marketplace return decision, published to marketplace-return-lifecycle-queue and stored verbatim in
 * {@code RMA.marketplaceDecisions}. It carries everything the listener needs, so a resend republishes the
 * same bytes without loading the order - which may have been hard-deleted since the decision was taken.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnLifecycleEvent(
        String storeId,
        String orderId,
        String externalOrderId,
        String marketplace,
        ReturnLifecycleEventType type,
        MarketplaceReturnAction action
) {
}
