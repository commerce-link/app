package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

/**
 * Reads the result of a cancel command sent by {@link ShipmentCancelService}. A result still being computed is
 * asked for again with a new delayed message (never by sleeping); the last attempt always leaves the shipment in a
 * final state, so nothing stays PENDING once the checks run out. The writes themselves are
 * {@link ShipmentCancellationSettler}'s.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentCancellationChecker {

    static final int MAX_ATTEMPTS = 6;

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final ShippingProviders shippingProviders;
    private final ShipmentCancellationEventPublisher publisher;
    private final ShipmentCancellationSettler settler;

    public void check(ShipmentCancellationCheckRequest request) {
        Order order = ordersRepository.findById(request.getStoreId(), request.getOrderId());
        if (ShipmentCancellationSettler.pendingShipment(order, request) == null) {
            log.debug("Cancellation check dropped, shipment no longer waits for it: store={} order={} command={}",
                    request.getStoreId(), request.getOrderId(), request.getCommandId());
            return;
        }
        ShippingProvider provider = provider(request.getStoreId(), request.getProvider());
        if (provider == null) {
            settler.unconfirmed(request);
            return;
        }
        ShipmentCancellation result;
        try {
            result = provider.checkShipmentCancellation(request.getCommandId(), request.getExternalId());
        } catch (RuntimeException e) {
            log.warn("Cancellation check failed store={} order={} command={} attempt={}: {}",
                    request.getStoreId(), request.getOrderId(), request.getCommandId(), request.getAttempt(), e.getMessage(), e);
            result = ShipmentCancellation.pending(request.getCommandId());
        }
        settler.reportOtherCancelledPackages(request, result);
        switch (result.status()) {
            case PENDING -> {
                if (request.getAttempt() < MAX_ATTEMPTS) {
                    publisher.publish(request.nextAttempt());
                } else {
                    settler.unconfirmed(request);
                }
            }
            case SUCCEEDED -> settler.succeed(request);
            case FAILED -> settler.fail(request, result.error());
        }
    }

    // the integration the command was sent to; a message from before the field existed belongs to the default one
    private ShippingProvider provider(String storeId, String providerName) {
        Store store = storesRepository.findById(storeId);
        return shippingProviders.forCommand(store, providerName).orElse(null);
    }
}
