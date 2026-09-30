package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks the shipping provider to cancel the courier order of the first dispatched shipment. The cancellation is only
 * requested here: the shipment is marked PENDING and {@link ShipmentCancellationChecker} clears it once the provider
 * confirms, or records why it did not.
 */
@Slf4j
@Service
public class ShipmentCancelService {

    static final String ALREADY_IN_PROGRESS = "Shipment cancellation is already in progress";

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final ShipmentCancellationEventPublisher publisher;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public ShipmentCancelService(StoresRepository storesRepository, OrdersRepository ordersRepository,
                                 ShippingProviderFactory shippingProviderFactory,
                                 ShipmentCancellationEventPublisher publisher,
                                 OptimisticLockingExecutor optimisticLockingExecutor) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.shippingProviderFactory = shippingProviderFactory;
        this.publisher = publisher;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
    }

    public void cancelShipping(String orderId, String storeId) {
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);

        Shipment shipment = order.firstShipmentWithShippingData()
                .orElseThrow(() -> new ShippingException("No valid shipment data to cancel"));

        String externalId = shipment.getExternalId();
        if (externalId == null) {
            throw new ShippingException("Shipment has no external package ID");
        }

        LocalDateTime now = LocalDateTime.now();
        if (shipment.isCancellationInProgress(now)) {
            throw new ShippingException(ALREADY_IN_PROGRESS);
        }

        // an unknown result is read again rather than cancelled anew: a late success of the old command would make
        // a new one fail and mark a cancelled package as not cancelled
        String commandId = shipment.needsCancellationRecheck(now)
                ? shipment.getCancellationCommandId()
                : shippingProviderFactory.get(store).cancelShipment(externalId).commandId();

        AtomicBoolean marked = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(storeId, orderId),
                fresh -> fresh.getShipments().stream()
                        .filter(s -> externalId.equals(s.getExternalId()))
                        .findFirst()
                        .ifPresent(s -> {
                            s.markCancellationPending(commandId, now);
                            marked.set(true);
                        }),
                fresh -> {
                    if (marked.get()) {
                        ordersRepository.save(fresh);
                    }
                });
        if (!marked.get()) {
            log.error("Cancel command {} for package {} was sent but order {} of store {} no longer has the shipment",
                    commandId, externalId, orderId, storeId);
            return;
        }
        publisher.publish(ShipmentCancellationCheckRequest.first(storeId, orderId, externalId, commandId));
    }
}
