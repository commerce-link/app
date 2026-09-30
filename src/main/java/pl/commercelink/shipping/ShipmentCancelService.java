package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCancellationStatus;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

        // an unknown result is read again rather than cancelled anew: a late success of the old command would make
        // a new one fail and mark a cancelled package as not cancelled
        LocalDateTime now = LocalDateTime.now();
        boolean recheck = shipment.needsCancellationRecheck(now);
        String commandId = recheck ? shipment.getCancellationCommandId() : UUID.randomUUID().toString();

        // the command is recorded before it is sent: a concurrent request then finds it in progress on its fresh read
        // and never sends a second command that would overwrite this one and fail on the already cancelled package
        AtomicBoolean marked = new AtomicBoolean();
        AtomicBoolean inProgress = new AtomicBoolean();
        AtomicReference<CancellationState> previous = new AtomicReference<>();
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(storeId, orderId),
                fresh -> {
                    // the executor runs load-mutate-save again after a version conflict: each attempt starts clean
                    marked.set(false);
                    inProgress.set(false);
                    previous.set(null);
                    findShipment(fresh, externalId).ifPresent(s -> {
                        if (s.isCancellationInProgress(now)) {
                            inProgress.set(true);
                            return;
                        }
                        previous.set(CancellationState.of(s));
                        s.markCancellationPending(commandId, now);
                        marked.set(true);
                    });
                },
                fresh -> {
                    if (marked.get()) {
                        ordersRepository.save(fresh);
                    }
                });
        if (inProgress.get()) {
            throw new ShipmentCancellationInProgressException();
        }
        if (!marked.get()) {
            log.warn("Order {} of store {} no longer has package {}; no cancel command was sent",
                    orderId, storeId, externalId);
            return;
        }

        if (!recheck) {
            try {
                shippingProviderFactory.get(store).cancelShipment(externalId, commandId);
            } catch (RuntimeException e) {
                restore(storeId, orderId, externalId, commandId, previous.get());
                throw e;
            }
        }
        publisher.publish(ShipmentCancellationCheckRequest.first(storeId, orderId, externalId, commandId));
    }

    /** The provider refused the command, so nothing is being cancelled: the shipment gets back its earlier state. */
    private void restore(String storeId, String orderId, String externalId, String commandId,
                         CancellationState previous) {
        try {
            AtomicBoolean restored = new AtomicBoolean();
            optimisticLockingExecutor.modifyAndSave(
                    () -> ordersRepository.findById(storeId, orderId),
                    fresh -> {
                        restored.set(false);
                        findShipment(fresh, externalId)
                                .filter(s -> s.hasCancellationCommand(commandId) && s.isCancellationPending())
                                .ifPresent(s -> {
                                    previous.applyTo(s);
                                    restored.set(true);
                                });
                    },
                    fresh -> {
                        if (restored.get()) {
                            ordersRepository.save(fresh);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Cancel command {} for package {} of order {} in store {} was refused by the provider, "
                    + "but the shipment stays marked PENDING: restoring its state failed", commandId, externalId,
                    orderId, storeId, e);
        }
    }

    private static Optional<Shipment> findShipment(Order order, String externalId) {
        return order.getShipments().stream()
                .filter(s -> externalId.equals(s.getExternalId()))
                .findFirst();
    }

    private record CancellationState(ShipmentCancellationStatus status, String commandId, String error,
                                     LocalDateTime requestedAt) {

        static CancellationState of(Shipment shipment) {
            return new CancellationState(shipment.getCancellationStatus(), shipment.getCancellationCommandId(),
                    shipment.getCancellationError(), shipment.getCancellationRequestedAt());
        }

        void applyTo(Shipment shipment) {
            shipment.restoreCancellation(status, commandId, error, requestedAt);
        }
    }
}
