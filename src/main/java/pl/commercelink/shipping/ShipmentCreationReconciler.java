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

    /**
     * The shipment whose command turned out to have created it: the caller must not book again. Empty when nothing was
     * created, or nothing more is knowable (still pending, the check failed, the integration is gone): the operator
     * was warned by the row's message.
     */
    public Optional<Shipment> reconcile(Store store, Order order) {
        LocalDateTime now = LocalDateTime.now();
        for (Shipment shipment : order.getShipments()) {
            ShipmentCreationState creation = shipment.getCreation();
            if (creation == null || creation.getCommand() == null || creation.getCommand().getCommandId() == null
                    || !creation.isOutcomeUnknown(now)) {
                continue;
            }
            if (foundCreated(store, order, shipment)) {
                return Optional.of(shipment);
            }
        }
        return Optional.empty();
    }

    private boolean foundCreated(Store store, Order order, Shipment shipment) {
        ShipmentCreationCheckRequest request = ShipmentCreationCheckRequest.builder()
                .storeId(order.getStoreId())
                .ownerType(ShipmentOwnerType.ORDER)
                .ownerId(order.getOrderId())
                .commandId(shipment.getCreation().getCommand().getCommandId())
                .externalId(shipment.getExternalId())
                .provider(shipment.getProvider())
                .pickUpAddressId(shipment.getPickUpAddressId())
                .build();
        Optional<ShippingProvider> provider = shippingProviders.forShipment(store, shipment);
        if (provider.isEmpty()) {
            log.warn("Creation command {} of order {} in store {} not checked again: its integration {} is gone",
                    request.getCommandId(), request.getOwnerId(), request.getStoreId(), request.getProvider());
            return false;
        }
        ShipmentCreation result;
        try {
            result = provider.get().checkShipmentCreation(request.getCommandId(), request.getExternalId());
        } catch (RuntimeException e) {
            log.warn("Creation command {} of order {} in store {} could not be checked again: {}",
                    request.getCommandId(), request.getOwnerId(), request.getStoreId(), e.getMessage(), e);
            return false;
        }
        switch (result.status()) {
            case SUCCEEDED -> {
                settleCreated(request.withExternalId(result.result().externalId()), result);
                return true;
            }
            case FAILED -> settleFailed(request, result.error());
            case PENDING -> log.warn("Creation command {} of order {} in store {} (package {}) is still pending",
                    request.getCommandId(), request.getOwnerId(), request.getStoreId(), result.externalId());
        }
        return false;
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
