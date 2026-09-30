package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reads the result of a cancel command sent by {@link ShipmentCancelService}. A result still being computed is
 * asked for again with a new delayed message (never by sleeping); the last attempt always leaves the shipment in a
 * final state, so nothing stays PENDING once the checks run out.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentCancellationChecker {

    static final int MAX_ATTEMPTS = 6;

    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final ShipmentCancellationEventPublisher publisher;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public void check(ShipmentCancellationCheckRequest request) {
        Order order = ordersRepository.findById(request.getStoreId(), request.getOrderId());
        if (order == null || pendingShipment(order, request) == null) {
            log.debug("Cancellation check dropped, shipment no longer waits for it: store={} order={} command={}",
                    request.getStoreId(), request.getOrderId(), request.getCommandId());
            return;
        }
        ShippingProvider provider = provider(request.getStoreId());
        if (provider == null) {
            update(request, Shipment::markCancellationUnconfirmed);
            return;
        }
        ShipmentCancellation result;
        try {
            result = provider.checkShipmentCancellation(request.getCommandId(), request.getExternalId());
        } catch (RuntimeException e) {
            log.warn("Cancellation check failed store={} order={} command={} attempt={}: {}",
                    request.getStoreId(), request.getOrderId(), request.getCommandId(), request.getAttempt(), e.getMessage());
            result = ShipmentCancellation.pending(request.getCommandId());
        }
        if (!result.otherCancelledPackageIds().isEmpty()) {
            // the provider reports more cancelled packages than the one asked for (see the Furgonetka pickup analysis)
            log.error("Cancelling package {} of store={} order={} also cancelled packages {}",
                    request.getExternalId(), request.getStoreId(), request.getOrderId(), result.otherCancelledPackageIds());
        }
        switch (result.status()) {
            case PENDING -> {
                if (request.getAttempt() < MAX_ATTEMPTS) {
                    publisher.publish(request.nextAttempt());
                } else {
                    update(request, Shipment::markCancellationUnconfirmed);
                }
            }
            case SUCCEEDED -> succeed(request);
            case FAILED -> {
                String error = result.error();
                update(request, shipment -> shipment.markCancellationFailed(error));
            }
        }
    }

    // what ShipmentCancelService did right away before cancellations were confirmed
    private void succeed(ShipmentCancellationCheckRequest request) {
        boolean cleared = modify(request, (order, shipment) ->
                order.replaceShipments(Collections.singletonList(new Shipment(shipment.getType()))));
        if (cleared) {
            orderEventsRepository.deleteByOrderIdAndName(request.getOrderId(), EmailNotificationType.ORDER_SHIPPING.name());
        }
    }

    private void update(ShipmentCancellationCheckRequest request, Consumer<Shipment> change) {
        modify(request, (order, shipment) -> change.accept(shipment));
    }

    // the order may have changed while the check was queued: apply to the freshly loaded one, and only while its
    // shipment still waits for this command; returns whether anything was saved
    private boolean modify(ShipmentCancellationCheckRequest request, java.util.function.BiConsumer<Order, Shipment> change) {
        AtomicBoolean changed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(request.getStoreId(), request.getOrderId()),
                fresh -> {
                    Shipment target = fresh == null ? null : pendingShipment(fresh, request);
                    changed.set(target != null);
                    if (target != null) {
                        change.accept(fresh, target);
                    }
                },
                fresh -> {
                    if (changed.get()) {
                        ordersRepository.save(fresh);
                    }
                });
        return changed.get();
    }

    private static Shipment pendingShipment(Order order, ShipmentCancellationCheckRequest request) {
        return order.getShipments().stream()
                .filter(s -> request.getExternalId().equals(s.getExternalId())
                        && s.isCancellationPending()
                        && s.hasCancellationCommand(request.getCommandId()))
                .findFirst()
                .orElse(null);
    }

    private ShippingProvider provider(String storeId) {
        Store store = storesRepository.findById(storeId);
        return store == null ? null : shippingProviderFactory.get(store);
    }
}
