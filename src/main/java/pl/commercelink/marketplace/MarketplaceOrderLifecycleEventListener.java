package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.marketplace.api.InvoiceUpdate;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.orders.*;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Optional;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class MarketplaceOrderLifecycleEventListener {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private MarketplaceProviderFactory providerFactory;

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
                // The same accept can be sent twice: the queue is at-least-once (redelivery,
                // DLQ redrive), and the app republishes OrderAccepted when an order's status
                // is rewound to New (OrderAllocationsManager.remove) and then moves forward
                // again. Empik's acceptOrder is a no-op, Morele rejects backward status
                // transitions, and a duplicate Ceneo ConfirmOrder is an accepted risk.
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
                extractShipmentUpdate(order)
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
            case StatusChange:
                break;
        }
    }

    private Optional<ShipmentUpdate> extractShipmentUpdate(Order order) {
        Optional<ShipmentUpdate> tracked = order.getShipments().stream()
                .filter(Shipment::hasShippingData)
                .findFirst()
                .map(s -> new ShipmentUpdate(s.getTrackingNo(), s.getCarrier(), s.getTrackingUrl()));

        if (tracked.isPresent()) {
            return tracked;
        }

        boolean hasCollectionShipment = order.getShipments().stream().anyMatch(Shipment::hasCollectionData);
        if (hasCollectionShipment) {
            return Optional.of(new ShipmentUpdate(null, null, null));
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
