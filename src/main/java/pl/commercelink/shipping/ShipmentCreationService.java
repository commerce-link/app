package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.shipping.api.CommandStatus;
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

    static final String UNCONFIRMED_KEY = ShipmentCreationState.UNCONFIRMED_KEY;

    private final ShippingService shippingService;
    private final ShipmentOwners owners;
    private final ShipmentCreationEventPublisher publisher;
    private final MessageSource messageSource;

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
                return refused(owner, check, ProviderErrors.describe(e), ProviderErrors.isProviderAnswer(e));
            }
            log.warn("Creation command {} of {} {} in store {} has an unknown outcome; it stays PENDING and is checked",
                    commandId, check.getOwnerType(), check.getOwnerId(), check.getStoreId(), e);
            return publishCheck(owner, check);
        }
        if (creation.externalId() != null) {
            check = check.withExternalId(creation.externalId());
            recordExternalId(owner, check);
        }
        if (creation.status() == CommandStatus.FAILED) {
            return refused(owner, check, creation.error(), true);
        }
        return publishCheck(owner, check);
    }

    private void recordExternalId(ShipmentOwner owner, ShipmentCreationCheckRequest check) {
        try {
            owner.recordExternalId(check);
        } catch (RuntimeException e) {
            // the check still runs: the checker finds the package by the command id
            log.error("Package {} of creation command {} for {} {} in store {} was not recorded on its owner",
                    check.getExternalId(), check.getCommandId(), check.getOwnerType(), check.getOwnerId(),
                    check.getStoreId(), e);
        }
    }

    private ShipmentCreationStart refused(ShipmentOwner owner, ShipmentCreationCheckRequest check, String reason,
                                          boolean providerAnswer) {
        try {
            owner.refused(check, reason);
        } catch (RuntimeException e) {
            log.error("Creation command {} for {} {} in store {} (package {}) was refused ({}), but its shipment stays "
                    + "PENDING: marking it failed did not work", check.getCommandId(), check.getOwnerType(),
                    check.getOwnerId(), check.getStoreId(), check.getExternalId(), reason, e);
        }
        return ShipmentCreationStart.refused(reason, providerAnswer);
    }

    /**
     * Without the check message nothing would ever settle the placeholder, and the owner could not book again: the
     * shipment is marked failed instead, so the operator checks the provider's panel and can retry or remove it.
     */
    private ShipmentCreationStart publishCheck(ShipmentOwner owner, ShipmentCreationCheckRequest check) {
        try {
            publisher.publish(check);
            return ShipmentCreationStart.started();
        } catch (RuntimeException e) {
            log.error("Check of creation command {} for {} {} in store {} (package {}) could not be sent; "
                    + "the provider may have created a paid label", check.getCommandId(), check.getOwnerType(),
                    check.getOwnerId(), check.getStoreId(), check.getExternalId(), e);
            return unconfirmed(owner, check);
        }
    }

    /** Stored as a key, so whoever opens the shipment later reads the reason in their own language. */
    private ShipmentCreationStart unconfirmed(ShipmentOwner owner, ShipmentCreationCheckRequest check) {
        try {
            owner.failed(check, null, UNCONFIRMED_KEY);
        } catch (RuntimeException e) {
            log.error("Creation command {} for {} {} in store {} (package {}) is unconfirmed, but its shipment stays "
                    + "PENDING: marking it failed did not work", check.getCommandId(), check.getOwnerType(),
                    check.getOwnerId(), check.getStoreId(), check.getExternalId(), e);
        }
        return ShipmentCreationStart.refused(messageSource.getMessage(UNCONFIRMED_KEY, null, LocaleContextHolder.getLocale()),
                false);
    }
}
