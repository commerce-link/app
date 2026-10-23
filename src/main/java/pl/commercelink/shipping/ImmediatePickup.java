package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Orders the pickup right after creation for shipments nobody books a courier for by hand: a customer's return
 * (picked up at the customer's address) and a warehouse shipment. The first window the carrier offers within
 * {@link #DAYS_AHEAD} days is taken, and a courier the carrier booked with the shipment is not ordered again. No window
 * fails the pickup instead of skipping it, since the package still needs a courier; what happens next is the owner's:
 * a customer's return (collected at the customer's) stays orderable from its RMA ("Zamów odbiór ponownie"), while a
 * warehouse shipment, which is stored nowhere in the app, can only be told to the store: its notification sends the
 * operator to the provider's panel or a carrier point. An ordered pickup is settled by its check, like one ordered on
 * the page; every other outcome is told to the owner here and returned to the caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImmediatePickup {

    static final int DAYS_AHEAD = 3;
    /** The reason of a pickup the carrier offered no window for: nobody comes until it is ordered again. */
    static final String NO_WINDOWS_KEY = "shipping.pickup.immediate.no.windows";

    private final StoresRepository storesRepository;
    private final ShipmentPickupService pickupService;
    private final ShipmentOwners owners;

    /**
     * What ordering the pickup did, for a caller that answers an operator at once ("Zamów odbiór ponownie"): the
     * command is on its way, the packages need no courier, the pickup failed (in the provider's words or a message key
     * of ours), or nothing waits any more.
     */
    public record Outcome(Kind kind, String error, String errorKey) {

        public enum Kind { STARTED, NOT_REQUIRED, FAILED, GONE }

        static Outcome started() {
            return new Outcome(Kind.STARTED, null, null);
        }

        static Outcome notRequired() {
            return new Outcome(Kind.NOT_REQUIRED, null, null);
        }

        static Outcome failed(String error, String errorKey) {
            return new Outcome(Kind.FAILED, error, errorKey);
        }

        static Outcome gone() {
            return new Outcome(Kind.GONE, null, null);
        }

        static Outcome of(ShipmentPickup failedPickup) {
            return failed(failedPickup.getCommand().getError(), failedPickup.getCommand().getErrorKey());
        }
    }

    public Outcome orderFor(ShipmentCreationCheckRequest creation, List<Shipment> created) {
        ShipmentOwner owner = owners.get(creation.getOwnerType());
        List<String> externalIds = created.stream().filter(Shipment::awaitsPickup)
                .map(Shipment::getExternalId).distinct().toList();
        if (externalIds.isEmpty()) {
            // handed in at a point, or the carrier booked the courier itself: the owner hears the pickup as created
            created.stream().map(Shipment::getExternalId).distinct().forEach(id -> owner.onPickupSettled(
                    creation.getStoreId(), creation.getProvider(), target(creation, id, created),
                    createdPickup(id, created)));
            return Outcome.notRequired();
        }
        Store store = storesRepository.findById(creation.getStoreId());
        List<PickupWindow> windows;
        try {
            windows = pickupService.windows(store, creation.getProvider(), creation.getPickUpAddressId(), externalIds,
                    DAYS_AHEAD);
        } catch (RuntimeException e) {
            // the owner reports the failed pickup in its own way (a notification, an error for a customer's return)
            log.warn("Pickup of {} {} in store {} was not ordered: its windows could not be read", creation.getOwnerType(),
                    creation.getOwnerId(), creation.getStoreId(), e);
            return settleAll(creation, owner, externalIds, created,
                    ShipmentPickup.awaiting().failed(ProviderErrors.describe(e)));
        }
        if (windows.isEmpty()) {
            return settleAll(creation, owner, externalIds, created,
                    ShipmentPickup.awaiting().failedWithKey(NO_WINDOWS_KEY));
        }
        PickupWindow window = windows.get(0);
        List<PickupTarget> targets = externalIds.stream().map(id -> target(creation, id, created)).toList();
        PickupStart start;
        try {
            start = pickupService.order(store, creation.getProvider(), creation.getPickUpAddressId(), targets,
                    window);
        } catch (RuntimeException e) {
            // the service logged it and failed what it had marked, so the pickup can be ordered again
            log.warn("Pickup of {} {} in store {} was not ordered: marking its packages broke off",
                    creation.getOwnerType(), creation.getOwnerId(), creation.getStoreId(), e);
            ShipmentPickup notSent = ShipmentPickup.awaiting().failedWithKey(ShipmentPickupService.NOT_SENT_KEY);
            tellAll(creation, owner, targets, notSent);
            return Outcome.of(notSent);
        }
        if (start.outcome() == PickupStart.Outcome.REFUSED) {
            // the service wrote the refusal on the owner; who should hear about it is the owner's call
            ShipmentPickup refused = ShipmentPickup.pending(start.commandId(), LocalDateTime.now(), window.date(),
                    window.from(), window.to()).failed(start.error());
            tellAll(creation, owner, targets, refused);
            return Outcome.of(refused);
        }
        if (start.outcome() == PickupStart.Outcome.GONE) {
            log.warn("Pickup of {} {} in store {} was not ordered: none of its packages {} waits any more",
                    creation.getOwnerType(), creation.getOwnerId(), creation.getStoreId(), externalIds);
            return Outcome.gone();
        }
        return Outcome.started();
    }

    private static Outcome settleAll(ShipmentCreationCheckRequest creation, ShipmentOwner owner,
                                     List<String> externalIds, List<Shipment> created, ShipmentPickup result) {
        if (owner.applyPickup(creation.getStoreId(), creation.getOwnerId(), externalIds,
                p -> p.isAwaiting() ? result : p) == 0) {
            return Outcome.gone();
        }
        tellAll(creation, owner, externalIds.stream().map(id -> target(creation, id, created)).toList(), result);
        return Outcome.of(result);
    }

    private static void tellAll(ShipmentCreationCheckRequest creation, ShipmentOwner owner, List<PickupTarget> targets,
                                ShipmentPickup result) {
        targets.forEach(t -> owner.onPickupSettled(creation.getStoreId(), creation.getProvider(), t, result));
    }

    private static ShipmentPickup createdPickup(String externalId, List<Shipment> created) {
        return created.stream().filter(s -> Objects.equals(externalId, s.getExternalId()))
                .map(Shipment::getPickup).filter(p -> p != null && p.isBookedByCarrier())
                .findFirst().orElseGet(ShipmentPickup::notRequired);
    }

    private static PickupTarget target(ShipmentCreationCheckRequest creation, String externalId, List<Shipment> created) {
        String trackingNo = created.stream().filter(s -> Objects.equals(externalId, s.getExternalId()))
                .map(Shipment::getTrackingNo).findFirst().orElse(null);
        return new PickupTarget(creation.getOwnerType(), creation.getOwnerId(), externalId, trackingNo);
    }
}
