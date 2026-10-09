package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.shipping.api.ShipmentCreation;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Asks once more, before an order's shipment is booked again, how a creation command with an unknown outcome ended
 * (overdue PENDING, or FAILED as never confirmed): the provider may have created and charged it after the checks ran out
 * (Allegro's SUCCESS before the waybill) or while the app was down during the call. One synchronous check on the
 * integration that got the command; what it finds is settled as the creation checker would have.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentCreationReconciler {

    private final ShippingProviders shippingProviders;
    private final ShipmentCreationSettler settler;
    private final ShipmentOwners owners;

    /** What the check found about an earlier command, and the row it concerns. */
    public enum Outcome {
        /** Created after all, now settled on the order: no second booking. */
        CREATED,
        /** The provider holds the package but has not finished it (no waybill yet): no second booking either. */
        CREATED_WITHOUT_NUMBER
    }

    public record Found(Outcome outcome, Shipment shipment) {
    }

    /**
     * A shipment the provider holds for an earlier command: the caller must not book again. That includes a row naming
     * a package whose command could not be checked (the check failed, the integration is gone or cannot be built).
     * Empty when nothing was created, or nothing more is knowable and no package is named: the operator was warned by
     * the row's message.
     */
    public Optional<Found> reconcile(Store store, Order order) {
        LocalDateTime now = LocalDateTime.now();
        for (Shipment shipment : order.getShipments()) {
            ShipmentCreationState creation = shipment.getCreation();
            if (creation == null || creation.getCommand() == null || creation.getCommand().getCommandId() == null
                    || !creation.isOutcomeUnknown(now)) {
                continue;
            }
            Optional<Outcome> outcome = check(store, order, shipment);
            if (outcome.isPresent()) {
                return Optional.of(new Found(outcome.get(), shipment));
            }
        }
        return Optional.empty();
    }

    private Optional<Outcome> check(Store store, Order order, Shipment shipment) {
        ShipmentCreationCheckRequest request = ShipmentCreationCheckRequest.builder()
                .storeId(order.getStoreId())
                .ownerType(ShipmentOwnerType.ORDER)
                .ownerId(order.getOrderId())
                .commandId(shipment.getCreation().getCommand().getCommandId())
                .externalId(shipment.getExternalId())
                .provider(shipment.getProvider())
                .pickUpAddressId(shipment.getPickUpAddressId())
                .build();
        ShipmentCreation result;
        try {
            Optional<ShippingProvider> provider = shippingProviders.forShipment(store, shipment);
            if (provider.isEmpty()) {
                log.warn("Creation command {} of order {} in store {} not checked again: its integration {} is gone",
                        request.getCommandId(), request.getOwnerId(), request.getStoreId(), request.getProvider());
                return unchecked(request);
            }
            result = provider.get().checkShipmentCreation(request.getCommandId(), request.getExternalId());
        } catch (RuntimeException e) {
            log.warn("Creation command {} of order {} in store {} could not be checked again: {}",
                    request.getCommandId(), request.getOwnerId(), request.getStoreId(), e.getMessage(), e);
            return unchecked(request);
        }
        switch (result.status()) {
            case SUCCEEDED -> {
                settleCreated(request.withExternalId(result.result().externalId()), result);
                return Optional.of(Outcome.CREATED);
            }
            case FAILED -> settleFailed(request, result.error());
            case PENDING -> {
                // a package id means the provider already holds the (paid) package, only its number is missing
                // (Allegro's SUCCESS before the waybill); without one nothing says the command ever reached it
                if (result.externalId() != null) {
                    recordPackageId(request, result.externalId());
                    return Optional.of(Outcome.CREATED_WITHOUT_NUMBER);
                }
                log.warn("Creation command {} of order {} in store {} is still pending without a package",
                        request.getCommandId(), request.getOwnerId(), request.getStoreId());
            }
        }
        return Optional.empty();
    }

    /**
     * A command that could not be checked: a package id on the row means the provider already holds a (paid) package,
     * and booking again would drop that row and pay twice, so it stops the booking like a pending package does.
     */
    private Optional<Outcome> unchecked(ShipmentCreationCheckRequest request) {
        return request.getExternalId() != null ? Optional.of(Outcome.CREATED_WITHOUT_NUMBER) : Optional.empty();
    }

    private void recordPackageId(ShipmentCreationCheckRequest request, String externalId) {
        if (externalId.equals(request.getExternalId())) {
            return;
        }
        try {
            owners.get(request.getOwnerType()).recordExternalId(request.withExternalId(externalId));
        } catch (RuntimeException e) {
            // the booking is refused either way; the next check names the package again
            log.warn("Package {} of creation command {} of order {} in store {} was not recorded on the order",
                    externalId, request.getCommandId(), request.getOwnerId(), request.getStoreId(), e);
        }
    }

    private void settleCreated(ShipmentCreationCheckRequest request, ShipmentCreation result) {
        try {
            settler.succeeded(request, result.result());
        } catch (RuntimeException e) {
            // the provider holds the shipment either way: no second booking, whether or not its number was saved
            log.error("Creation command {} of order {} in store {} created package {}, but saving it on the order failed",
                    request.getCommandId(), request.getOwnerId(), request.getStoreId(), request.getExternalId(), e);
        }
    }

    private void settleFailed(ShipmentCreationCheckRequest request, String error) {
        try {
            settler.failed(request, error);
        } catch (RuntimeException e) {
            // the provider refused it, so booking again is safe; the new booking replaces the failed row anyway
            log.warn("Creation command {} of order {} in store {} was refused, but saving that on the order failed",
                    request.getCommandId(), request.getOwnerId(), request.getStoreId(), e);
        }
    }
}
