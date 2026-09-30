package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCancellationStatus;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asks the shipping provider to cancel the courier order of the first dispatched shipment. The shipment is marked
 * PENDING first; a result the provider gives right away is settled here by {@link ShipmentCancellationSettler},
 * otherwise {@link ShipmentCancellationChecker} clears the shipment once the provider confirms, or records why not.
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

        Shipment shipment = order.firstShipmentWithShippingData()
                .orElseThrow(() -> new ShippingException("No valid shipment data to cancel"));

        String externalId = shipment.getExternalId();
        if (externalId == null) {
            throw new ShippingException("Shipment has no external package ID");
        }
        // resolved before the mark: without a provider nothing can be sent, and a missing one must not look like a
        // command with an unknown outcome
        ShippingProvider provider = shippingProviderFactory.get(store);
        if (provider == null) {
            throw new NoShippingProviderException();
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
                        // the decision taken on the first read holds only if the fresh read still leads to it;
                        // otherwise another request changed the cancellation in between
                        if (s.isCancellationInProgress(now)
                                || s.needsCancellationRecheck(now) != recheck
                                || (recheck && !s.hasCancellationCommand(commandId))) {
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
            if (isRefusal(e)) {
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
            case SUCCEEDED -> {
                settler.succeed(check);
                yield ShipmentCancelResult.cancelled();
            }
            case FAILED -> {
                settler.fail(check, result.error());
                yield ShipmentCancelResult.failed(result.error());
            }
        };
    }

    /**
     * Only a clear refusal means the command was not run: a check before sending (no HTTP answer behind it) or a 4xx
     * answer. A 5xx, a timeout or any other error may come after the provider already accepted the command.
     */
    static boolean isRefusal(RuntimeException e) {
        HttpClientException http = httpCause(e);
        if (http != null) {
            return http.getStatusCode() >= 400 && http.getStatusCode() < 500;
        }
        return e instanceof ShippingException;
    }

    private static HttpClientException httpCause(Throwable e) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = e; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof HttpClientException http) {
                return http;
            }
        }
        return null;
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
        if (order == null) {
            return Optional.empty();
        }
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
