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
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Writes the final result of a cancel command to the order, for {@link ShipmentCancellationChecker} and for an
 * immediate result in {@link ShipmentCancelService}. Each write re-reads the order and applies only while its
 * shipment still waits for that very command, so a late or stale result never overwrites a newer state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentCancellationSettler {

    private final OrdersRepository ordersRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    // what ShipmentCancelService did right away before cancellations were confirmed
    public boolean succeed(ShipmentCancellationCheckRequest request) {
        boolean cleared = modify(request, (order, shipment) ->
                order.replaceShipments(Collections.singletonList(new Shipment(shipment.getType()))));
        if (cleared) {
            orderEventsRepository.deleteByOrderIdAndName(request.getOrderId(), EmailNotificationType.ORDER_SHIPPING.name());
        }
        return cleared;
    }

    public boolean fail(ShipmentCancellationCheckRequest request, String error) {
        return modify(request, (order, shipment) -> shipment.markCancellationFailed(error));
    }

    public boolean unconfirmed(ShipmentCancellationCheckRequest request) {
        return modify(request, (order, shipment) -> shipment.markCancellationUnconfirmed());
    }

    void reportOtherCancelledPackages(ShipmentCancellationCheckRequest request, ShipmentCancellation result) {
        if (!result.otherCancelledPackageIds().isEmpty()) {
            // the provider reports more cancelled packages than the one asked for (see the Furgonetka pickup analysis)
            log.error("Cancelling package {} of store={} order={} also cancelled packages {}",
                    request.getExternalId(), request.getStoreId(), request.getOrderId(), result.otherCancelledPackageIds());
        }
    }

    // the order may have changed meanwhile or be gone: apply to the freshly loaded one and never throw inside the
    // mutator, which the executor would only wrap
    private boolean modify(ShipmentCancellationCheckRequest request, BiConsumer<Order, Shipment> change) {
        AtomicBoolean changed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(request.getStoreId(), request.getOrderId()),
                fresh -> {
                    Shipment target = pendingShipment(fresh, request);
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

    static Shipment pendingShipment(Order order, ShipmentCancellationCheckRequest request) {
        if (order == null) {
            return null;
        }
        return order.getShipments().stream()
                .filter(s -> request.getExternalId().equals(s.getExternalId())
                        && s.isCancellationPending()
                        && s.hasCancellationCommand(request.getCommandId()))
                .findFirst()
                .orElse(null);
    }
}
