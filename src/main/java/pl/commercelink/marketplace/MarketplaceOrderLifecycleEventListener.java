package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.marketplace.api.InvoiceUpdate;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.orders.*;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Optional;
import java.util.function.Consumer;
import pl.commercelink.stores.IntegrationType;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class MarketplaceOrderLifecycleEventListener {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MarketplaceOrderLifecycleEventListener.class);

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private MarketplaceProviderFactory providerFactory;

    @Autowired
    private CarrierDictionary carrierDictionary;

    @SqsListener(
            value = "marketplace-order-lifecycle-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(OrderLifecycleEvent payload) {
        Store store = storesRepository.findById(payload.getStoreId());
        Order order = ordersRepository.findById(payload.getStoreId(), payload.getOrderId());

        // The order may have been hard-deleted (cancel-on-delete) before this runs; in that
        // case marketplace and external id come from the self-describing payload.
        if (order != null && !order.isMarketplaceOrder()) {
            return;
        }

        String marketplace = order != null ? order.getSource().getName() : payload.getMarketplace();
        if (marketplace == null) {
            return;
        }
        String externalOrderId = order != null ? order.getExternalOrderId() : payload.getExternalOrderId();

        MarketplaceIntegration integration = store.getMarketplaceIntegration(marketplace);
        if (integration == null) {
            return;
        }
        // a logged-out integration must fail loud so SQS retries until the store
        // re-authenticates; a silent skip would lose the event permanently
        if (!integration.isLoggedIn()) {
            throw new IllegalStateException("Marketplace integration " + marketplace
                    + " for store " + payload.getStoreId() + " is not authenticated");
        }

        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }

        switch (payload.getType()) {
            case OrderAccepted:
                if (order == null || order.getStatus() == OrderStatus.Cancelled) {
                    break;
                }
                provider.acceptOrder(externalOrderId);
                break;
            case ShipmentCreated:
                if (order == null) {
                    break;
                }
                // a terminal status is persisted before this listener runs; shipping
                // after complete/cancel would regress the marketplace state
                if (order.getStatus().isOneOf(OrderStatus.Completed, OrderStatus.Cancelled)) {
                    break;
                }
                extractShipmentUpdate(order, store, marketplace)
                        .ifPresent(update -> provider.shipOrder(externalOrderId, update));
                break;
            case OrderCancelled:
                provider.cancelOrder(externalOrderId);
                break;
            case OrderCompleted:
                if (order == null || order.getStatus() == OrderStatus.Cancelled) {
                    break;
                }
                provider.completeOrder(externalOrderId);
                break;
            case InvoiceCreated:
                if (order == null) {
                    break;
                }
                extractInvoiceUpdate(order)
                        .ifPresent(update -> provider.updateInvoice(externalOrderId, update));
                break;
            case ReturnAccepted:
                withReturns(provider, payload, returns -> returns.refundReturn(externalOrderId,
                        payload.getReturnAction().getExternalReturnId(), toReturnRefund(payload.getReturnAction())));
                break;
            case ReturnRejected:
                withReturns(provider, payload, returns -> returns.rejectReturn(
                        payload.getReturnAction().getExternalReturnId(),
                        new ReturnRejection(payload.getReturnAction().getRejectionReason())));
                break;
            case StatusChange:
                break;
        }
    }

    // a return event for a marketplace without a returns API cannot be acted on; skipping (not throwing)
    // keeps it out of the DLQ, and the RMA history shows whether the decision reached the marketplace
    private void withReturns(MarketplaceProvider provider, OrderLifecycleEvent payload,
                             Consumer<MarketplaceReturns> action) {
        if (payload.getReturnAction() == null || payload.getReturnAction().getExternalReturnId() == null) {
            LOGGER.warn("Return event {} for order {} has no return action; skipped", payload.getType(), payload.getOrderId());
            return;
        }
        Optional<MarketplaceReturns> returns = provider.returns();
        if (returns.isEmpty()) {
            LOGGER.error("Marketplace {} exposes no returns API, but {} decision for RMA {} (order {}) requires one - decision dropped; check the deployed adapter version",
                    payload.getMarketplace(), payload.getType(), payload.getReturnAction().getRmaId(), payload.getExternalOrderId());
            return;
        }
        action.accept(returns.get());
    }

    private static ReturnRefund toReturnRefund(MarketplaceReturnAction action) {
        return new ReturnRefund(
                action.getItems().stream()
                        .map(i -> new ReturnRefund.Item(i.getManufacturerCode(), i.getQuantity()))
                        .toList(),
                action.isRefundDelivery(),
                action.getCommandId(),
                action.getExternalReturnReference());
    }

    private Optional<ShipmentUpdate> extractShipmentUpdate(Order order, Store store, String marketplace) {
        Optional<ShipmentUpdate> tracked = order.getShipments().stream()
                .filter(Shipment::hasShippingData)
                .findFirst()
                .map(s -> new ShipmentUpdate(s.getTrackingNo(),
                        carrierDictionary.translate(store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER), marketplace, s.getCarrier()).orElse(null),
                        s.getCarrier(),
                        s.getTrackingUrl()));

        if (tracked.isPresent()) {
            return tracked;
        }

        boolean hasCollectionShipment = order.getShipments().stream().anyMatch(Shipment::hasCollectionData);
        if (hasCollectionShipment) {
            return Optional.of(new ShipmentUpdate(null, null, null, null));
        }

        return Optional.empty();
    }

    private Optional<InvoiceUpdate> extractInvoiceUpdate(Order order) {
        return order.getDocuments().stream()
                .filter(Document::hasNumberAndLink)
                .filter(d -> d.getType().isClosingInvoice())
                .findFirst()
                .map(d -> new InvoiceUpdate(d.getNumber(), d.getLink()));
    }
}
