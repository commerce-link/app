package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.marketplace.api.InvoiceUpdate;
import pl.commercelink.marketplace.api.MarketplaceOrderStatus;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.orders.*;
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

        if (!order.isMarketplaceOrder()) {
            return;
        }

        String marketplace = order.getSource().getName();

        if (!store.hasActiveMarketplaceIntegration(marketplace)) {
            return;
        }

        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }

        String externalOrderId = order.getExternalOrderId();

        switch (payload.getType()) {
            case StatusChange:
                if (order.getStatus() != OrderStatus.New && order.getStatus() != OrderStatus.Blocked) {
                    provider.updateOrderStatus(externalOrderId, mapOrderStatus(order.getStatus()));
                }
                break;
            case ShipmentCreated:
                extractShipmentUpdate(order)
                        .ifPresent(update -> provider.updateShipment(externalOrderId, update));
                break;
            case InvoiceCreated:
                extractInvoiceUpdate(order)
                        .ifPresent(update -> provider.updateInvoice(externalOrderId, update));
                break;
        }
    }

    private Optional<ShipmentUpdate> extractShipmentUpdate(Order order) {
        return order.getShipments().stream()
                .filter(Shipment::hasShippingData)
                .findFirst()
                .map(s -> new ShipmentUpdate(s.getTrackingNo(), s.getCarrier(), s.getTrackingUrl()));
    }

    private Optional<InvoiceUpdate> extractInvoiceUpdate(Order order) {
        return order.getDocuments().stream()
                .filter(Document::hasNumberAndLink)
                .filter(d -> d.getType().isClosingInvoice())
                .findFirst()
                .map(d -> new InvoiceUpdate(d.getNumber(), d.getLink()));
    }

    private MarketplaceOrderStatus mapOrderStatus(OrderStatus orderStatus) {
        switch (orderStatus) {
            case Shipping:
                return MarketplaceOrderStatus.Shipping;
            case Delivered:
                return MarketplaceOrderStatus.Delivered;
            case Completed:
                return MarketplaceOrderStatus.Completed;
            default:
                return MarketplaceOrderStatus.InProgress;
        }
    }
}
