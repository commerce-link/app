package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.shipping.api.PickupWindow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Writes the result of a pickup command to every owner of its packages, for {@link ShipmentPickupChecker}. A write
 * applies only while the pickup is pending for that very command, so a late result of an earlier command changes
 * nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentPickupSettler {

    private final ShipmentOwners owners;
    private final AwaitingPickupIndex index;

    public void ordered(ShipmentPickupCheckRequest request, String pickupId) {
        settle(request, p -> p.ordered(pickupId), true);
    }

    public void failed(ShipmentPickupCheckRequest request, String error) {
        log.warn("Pickup failed store={} command={} packages={}: {}", request.getStoreId(), request.getCommandId(),
                externalIds(request), error);
        settle(request, p -> p.failed(error), false);
    }

    /** Our own reason (never confirmed, no provider): the courier may still come, the provider's panel says. */
    public void failedWithKey(ShipmentPickupCheckRequest request, String key) {
        log.error("Pickup ended without a result store={} command={} packages={}: {}", request.getStoreId(),
                request.getCommandId(), externalIds(request), key);
        settle(request, p -> p.failedWithKey(key), false);
    }

    private void settle(ShipmentPickupCheckRequest request, UnaryOperator<ShipmentPickup> result, boolean leaveIndex) {
        UnaryOperator<ShipmentPickup> change = p -> p.isPendingFor(request.getCommandId()) ? result.apply(p) : p;
        for (PickupTarget target : request.getTargets()) {
            ShipmentOwner owner = owners.get(target.ownerType());
            // the result the owner wrote, for its follow-up (an e-mail, a notification)
            AtomicReference<ShipmentPickup> written = new AtomicReference<>();
            int changed = owner.applyPickup(request.getStoreId(), target.ownerId(), List.of(target.externalId()), p -> {
                ShipmentPickup next = change.apply(p);
                if (next != p) {
                    written.set(next);
                }
                return next;
            });
            if (changed == 0) {
                log.warn("Pickup result dropped, nothing waits for it: store={} {} {} package={} command={}",
                        request.getStoreId(), target.ownerType(), target.ownerId(), target.externalId(),
                        request.getCommandId());
                continue;
            }
            if (leaveIndex) {
                leaveIndex(request, target);
            }
            // an owner without stored shipments (the warehouse) never runs the change: its outcome is computed here
            ShipmentPickup outcome = written.get() != null ? written.get() : result.apply(pendingOf(request));
            try {
                owner.onPickupSettled(request.getStoreId(), request.getProvider(), target, outcome);
            } catch (RuntimeException e) {
                // the pickup is saved, so a redelivery would be dropped and could not run the follow-up either
                log.error("Pickup of package {} ({} {}) in store {} was settled, but what it sets off failed",
                        target.externalId(), target.ownerType(), target.ownerId(), request.getStoreId(), e);
            }
        }
    }

    private void leaveIndex(ShipmentPickupCheckRequest request, PickupTarget target) {
        try {
            index.remove(request.getStoreId(), List.of(target.externalId()));
        } catch (RuntimeException e) {
            // the entry no longer waits, so the pickup page drops it when it reads it
            log.warn("Ordered package {} of store {} is still in the pickup index", target.externalId(),
                    request.getStoreId(), e);
        }
    }

    private static List<String> externalIds(ShipmentPickupCheckRequest request) {
        return request.getTargets().stream().map(PickupTarget::externalId).toList();
    }

    private static ShipmentPickup pendingOf(ShipmentPickupCheckRequest request) {
        PickupWindow window = request.window();
        return ShipmentPickup.pending(request.getCommandId(), LocalDateTime.now(), window.date(), window.from(),
                window.to());
    }
}
