package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.shipping.api.PickupWindow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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

    public void ordered(ShipmentPickupCheckRequest request, String pickupId) {
        settle(request, request.getTargets(), p -> p.ordered(pickupId));
    }

    /**
     * A command that succeeded for some of its packages only: the packages the provider listed are ordered, the others
     * fail as unconfirmed and are listed again, so the operator checks the provider's panel and can order them again.
     */
    public void ordered(ShipmentPickupCheckRequest request, String pickupId, Collection<String> orderedIds) {
        Map<Boolean, List<PickupTarget>> listed = request.getTargets().stream()
                .collect(Collectors.partitioningBy(t -> orderedIds.contains(t.externalId())));
        settle(request, listed.get(true), p -> p.ordered(pickupId));
        if (!listed.get(false).isEmpty()) {
            List<String> left = listed.get(false).stream().map(PickupTarget::externalId).toList();
            log.error("Pickup command {} in store {} succeeded without packages {}", request.getCommandId(),
                    request.getStoreId(), left);
            settle(request, listed.get(false), p -> p.failedWithKey(ShipmentPickup.UNCONFIRMED_KEY));
        }
    }

    public void failed(ShipmentPickupCheckRequest request, String error) {
        log.warn("Pickup failed store={} command={} packages={}: {}", request.getStoreId(), request.getCommandId(),
                externalIds(request), error);
        settle(request, request.getTargets(), p -> p.failed(error));
    }

    /** Our own reason (never confirmed, no provider): the courier may still come, the provider's panel says. */
    public void failedWithKey(ShipmentPickupCheckRequest request, String key) {
        log.error("Pickup ended without a result store={} command={} packages={}: {}", request.getStoreId(),
                request.getCommandId(), externalIds(request), key);
        settle(request, request.getTargets(), p -> p.failedWithKey(key));
    }

    private void settle(ShipmentPickupCheckRequest request, List<PickupTarget> targets,
                        UnaryOperator<ShipmentPickup> result) {
        UnaryOperator<ShipmentPickup> change = p -> p.isPendingFor(request.getCommandId()) ? result.apply(p) : p;
        for (PickupTarget target : targets) {
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

    private static List<String> externalIds(ShipmentPickupCheckRequest request) {
        return request.getTargets().stream().map(PickupTarget::externalId).toList();
    }

    private static ShipmentPickup pendingOf(ShipmentPickupCheckRequest request) {
        PickupWindow window = request.window();
        return ShipmentPickup.pending(request.getCommandId(), LocalDateTime.now(), window.date(), window.from(),
                window.to());
    }
}
