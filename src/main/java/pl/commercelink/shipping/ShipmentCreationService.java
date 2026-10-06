package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.shipping.api.ShipmentCreation;
import pl.commercelink.shipping.api.ShipmentRequest;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Starts creating a shipment: the owner keeps a placeholder waiting for the command before the provider is called, so
 * a crash in between leaves something to check instead of a paid label nobody knows of. The result is settled by
 * the creation checker from shipment-creation-queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentCreationService {

    private final ShippingService shippingService;
    private final ShipmentOwners owners;
    private final ShipmentCreationEventPublisher publisher;

    public ShipmentCreationStart start(ShipmentCreationCheckRequest seed, ShipmentRequest request, Store store,
                                       Shipment placeholder) {
        ShippingProvider provider = shippingService.providerFor(store);
        String commandId = UUID.randomUUID().toString();
        ShipmentCreationCheckRequest check = seed.toBuilder()
                .commandId(commandId)
                .provider(shippingService.providerName(store))
                .attempt(1)
                .build();
        placeholder.setProvider(check.getProvider());
        placeholder.setPickUpAddressId(check.getPickUpAddressId());
        placeholder.setCreation(ShipmentCreationState.pending(commandId, LocalDateTime.now()));

        ShipmentOwner owner = owners.get(check.getOwnerType());
        if (!owner.markCreating(check, placeholder)) {
            log.warn("{} {} of store {} is gone or already has a shipment being created; no creation command was sent",
                    check.getOwnerType(), check.getOwnerId(), check.getStoreId());
            return ShipmentCreationStart.gone();
        }

        ShipmentCreation creation;
        try {
            creation = provider.createShipment(request, commandId);
        } catch (RuntimeException e) {
            if (ProviderErrors.isRefusal(e)) {
                String reason = ProviderErrors.describe(e);
                owner.refused(check, reason);
                return ShipmentCreationStart.refused(reason);
            }
            log.warn("Creation command {} of {} {} in store {} has an unknown outcome; it stays PENDING and is checked",
                    commandId, check.getOwnerType(), check.getOwnerId(), check.getStoreId(), e);
            publisher.publish(check);
            return ShipmentCreationStart.started();
        }
        if (creation.externalId() != null) {
            check = check.withExternalId(creation.externalId());
            owner.recordExternalId(check);
        }
        switch (creation.status()) {
            case FAILED -> {
                owner.refused(check, creation.error());
                return ShipmentCreationStart.refused(creation.error());
            }
            case PENDING, SUCCEEDED -> publisher.publish(check);
        }
        return ShipmentCreationStart.started();
    }
}
