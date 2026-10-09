package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.CourierCancellation;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asks the shipping provider to cancel the first courier order on the list whose parcel is not delivered. The shipment is marked
 * PENDING first; a result the provider gives right away is settled here by {@link ShipmentCancellationSettler},
 * otherwise {@link ShipmentCancellationChecker} clears the shipment once the provider confirms, or marks it failed or
 * unconfirmed.
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
    private final ShipmentCancellationSettler settler;

    public ShipmentCancelService(StoresRepository storesRepository, OrdersRepository ordersRepository,
                                 ShippingProviderFactory shippingProviderFactory,
                                 ShipmentCancellationEventPublisher publisher,
                                 OptimisticLockingExecutor optimisticLockingExecutor,
                                 ShipmentCancellationSettler settler) {
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.shippingProviderFactory = shippingProviderFactory;
        this.publisher = publisher;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.settler = settler;
    }

    public ShipmentCancelResult cancelShipping(String orderId, String storeId) {
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);

        // by the courier order, not by the shipped date: the paid label is there whatever the dates say
        Shipment shipment = order.courierShipmentToCancel()
                .orElseThrow(() -> new ShippingException("No courier order to cancel"));
        String externalId = shipment.getExternalId();
        // resolved before the mark: without a provider nothing can be sent, and a missing one must not look like a
        // command with an unknown outcome
        ShippingProvider provider = shippingProviderFactory.get(store);
        if (provider == null) {
            throw new ShippingUnavailableException(storeId);
        }

        // an unknown result is read again rather than cancelled anew: a late success of the old command would make
        // a new one fail and mark a cancelled package as not cancelled
        LocalDateTime now = LocalDateTime.now();
        boolean recheck = shipment.needsCancellationRecheck(now);
        String commandId = recheck ? shipment.getCancellation().getCommandId() : UUID.randomUUID().toString();

        // the command is recorded before it is sent: a concurrent request then finds it in progress on its fresh read
        // and never sends a second command that would overwrite this one and fail on the already cancelled package
        AtomicBoolean marked = new AtomicBoolean();
        AtomicBoolean inProgress = new AtomicBoolean();
        // the cancellation before the mark (null when there was none), put back when the provider refuses the command
        AtomicReference<CourierCancellation> previous = new AtomicReference<>();
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(storeId, orderId),
                fresh -> {
                    // the executor runs load-mutate-save again after a version conflict: each attempt starts clean
                    marked.set(false);
                    inProgress.set(false);
                    previous.set(null);
                    findShipment(fresh, externalId).ifPresent(s -> {
                        // the decision taken on the first read holds only if the fresh read still leads to it;
                        // otherwise another request changed the cancellation in between
                        if (s.isCancellationInProgress(now)
                                || s.needsCancellationRecheck(now) != recheck
                                || (recheck && !s.getCancellation().hasCommand(commandId))) {
                            inProgress.set(true);
                            return;
                        }
                        previous.set(s.getCancellation());
                        s.setCancellation(CourierCancellation.pending(commandId, now));
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
            return ShipmentCancelResult.gone();
        }

        ShipmentCancellationCheckRequest check = ShipmentCancellationCheckRequest.first(storeId, orderId, externalId, commandId);
        if (recheck) {
            publisher.publish(check);
            return ShipmentCancelResult.rechecking();
        }

        ShipmentCancellation result;
        try {
            result = provider.cancelShipment(externalId, commandId);
        } catch (RuntimeException e) {
            if (ProviderErrors.isRefusal(e)) {
                restore(storeId, orderId, externalId, commandId, previous.get());
                throw e;
            }
            // the command id is ours and already recorded, so the checker finds out whether the command ran
            log.warn("Cancel command {} for package {} of order {} in store {} has an unknown outcome; "
                    + "it stays PENDING and its result will be checked", commandId, externalId, orderId, storeId, e);
            publisher.publish(check);
            return ShipmentCancelResult.requested();
        }
        settler.reportOtherCancelledPackages(check, result);
        return switch (result.status()) {
            case PENDING -> {
                publisher.publish(check);
                yield ShipmentCancelResult.requested();
            }
            case SUCCEEDED -> ShipmentCancelResult.cancelled(settler.succeed(check).backToRealization());
            case FAILED -> {
                settler.fail(check, result.error());
                yield ShipmentCancelResult.failed(result.error());
            }
        };
    }

    /** The provider refused the command, so nothing is being cancelled: the shipment gets back its earlier state. */
    private void restore(String storeId, String orderId, String externalId, String commandId,
                         CourierCancellation previous) {
        try {
            AtomicBoolean restored = new AtomicBoolean();
            optimisticLockingExecutor.modifyAndSave(
                    () -> ordersRepository.findById(storeId, orderId),
                    fresh -> {
                        restored.set(false);
                        findShipment(fresh, externalId)
                                .filter(s -> s.isCancellationPendingFor(commandId))
                                .ifPresent(s -> {
                                    s.setCancellation(previous);
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
        if (order == null) {
            return Optional.empty();
        }
        return order.getShipments().stream()
                .filter(s -> externalId.equals(s.getExternalId()))
                .findFirst();
    }
}
