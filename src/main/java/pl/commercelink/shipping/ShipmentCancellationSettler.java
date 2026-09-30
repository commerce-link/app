package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
    private final OrderRealizationStepBack realizationStepBack;

    /** What a confirmed cancellation did: whether the shipments were cleared and whether the order went back. */
    public record Success(boolean cleared, boolean backToRealization) {
    }

    /**
     * The cancellation is confirmed: the shipments of that courier order (every parcel carries its externalId) are
     * removed and the other shipments stay as they are. When no shipment is left, a bare one of the same type keeps the
     * customer's delivery choice (replaceShipments). When what is left of a Shipping order has nothing shipped it goes
     * back to Realization (OrderRealizationStepBack, no e-mail to the customer), and the shipping e-mail is forgotten
     * once no other shipment carries it.
     */
    public Success succeed(ShipmentCancellationCheckRequest request) {
        AtomicBoolean backToRealization = new AtomicBoolean();
        AtomicBoolean stepBackRecorded = new AtomicBoolean();
        AtomicBoolean nothingAnnounced = new AtomicBoolean();
        boolean cleared = modify(request, (order, shipment) -> {
            // each attempt of the executor starts clean, except for the step-back event: it is saved once, before the
            // first save of the order, so a retry after a version conflict does not record it twice
            backToRealization.set(false);
            nothingAnnounced.set(false);
            String courierOrderId = request.getExternalId();
            List<Shipment> remaining = order.getShipments().stream()
                    .filter(s -> !courierOrderId.equals(s.getExternalId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (remaining.isEmpty()) {
                // the list still holds the cancelled shipments, whose delivery choice the bare one inherits
                order.replaceShipments(new ArrayList<>(List.of(new Shipment(shipment.getType()))));
            } else {
                order.setShipments(remaining);
            }
            boolean back = stepBackRecorded.get()
                    ? order.returnToRealizationWhenNothingShipped()
                    : realizationStepBack.apply(order);
            if (back) {
                stepBackRecorded.set(true);
            }
            backToRealization.set(back);
            nothingAnnounced.set(order.firstShipmentWithShippingData().isEmpty());
        });
        if (cleared && nothingAnnounced.get()) {
            orderEventsRepository.deleteByOrderIdAndName(request.getOrderId(), EmailNotificationType.ORDER_SHIPPING.name());
        }
        return new Success(cleared, cleared && backToRealization.get());
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
