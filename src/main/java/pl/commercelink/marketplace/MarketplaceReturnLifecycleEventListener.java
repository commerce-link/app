package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.orders.MarketplaceReturnAction;
import pl.commercelink.orders.rma.ReturnLifecycleEvent;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class MarketplaceReturnLifecycleEventListener {

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory providerFactory;

    @SqsListener(
            value = "marketplace-return-lifecycle-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(ReturnLifecycleEvent event) {
        if (event.type() == null || event.action() == null || event.action().externalReturnId() == null) {
            log.error("Incomplete return decision for order {}; dropped without calling the marketplace",
                    event.orderId());
            return;
        }

        // No order is loaded on purpose: the event is self-describing, so a decision stays actionable even
        // when the order was hard-deleted (cancel-on-delete) between the publish and this delivery.
        Store store = storesRepository.findById(event.storeId());
        MarketplaceIntegration integration = store.getMarketplaceIntegration(event.marketplace());
        if (integration == null) {
            return;
        }
        // a logged-out integration must fail loud so SQS retries until the store re-authenticates;
        // a silent skip would lose a money decision permanently
        if (!integration.isLoggedIn()) {
            throw new IllegalStateException("Marketplace integration " + event.marketplace()
                    + " for store " + event.storeId() + " is not authenticated");
        }

        MarketplaceProvider provider = providerFactory.get(store, event.marketplace());
        if (provider == null) {
            return;
        }
        // retrying cannot add a returns API to a deployed adapter, so this is logged and dropped rather
        // than thrown; the decision stays on the RMA and the resend button works after a redeploy
        Optional<MarketplaceReturns> returns = provider.returns();
        if (returns.isEmpty()) {
            log.error("Marketplace {} exposes no returns API, but a {} decision for RMA {} (order {}) requires one"
                            + " - decision dropped; check the deployed adapter version",
                    event.marketplace(), event.type(), event.action().rmaId(), event.externalOrderId());
            return;
        }

        MarketplaceReturnAction action = event.action();
        switch (event.type()) {
            case ReturnAccepted -> returns.get().refundReturn(event.externalOrderId(),
                    action.externalReturnId(), toReturnRefund(action));
            case ReturnRejected -> returns.get().rejectReturn(action.externalReturnId(),
                    new ReturnRejection(action.rejectionReason()));
        }
    }

    private static ReturnRefund toReturnRefund(MarketplaceReturnAction action) {
        return new ReturnRefund(
                action.items().stream()
                        .map(i -> new ReturnRefund.Item(i.marketplaceKey(), i.quantity()))
                        .toList(),
                action.refundDelivery(),
                action.commandId());
    }
}
