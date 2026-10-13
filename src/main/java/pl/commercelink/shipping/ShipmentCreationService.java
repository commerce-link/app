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
import java.util.Locale;
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
    static final String NOT_CREATED_KEY = "shipping.creation.notCreated";

    private final ShippingService shippingService;
    private final ShippingProviders shippingProviders;
    private final ShipmentOwners owners;
    private final ShipmentCreationEventPublisher publisher;
    private final MessageSource messageSource;
    private final ShippingIntegrationNames shippingIntegrationNames;

    /** Through the store's default integration (RMA, customer returns, the warehouse, the default steps of an order). */
    public ShipmentCreationStart start(ShipmentCreationCheckRequest seed, ShipmentRequest request, Store store,
                                       Shipment placeholder) {
        return start(seed, request, store, placeholder, shippingService.providerFor(store),
                shippingService.providerName(store));
    }

    /** Through the integration the operator chose for an order ("Wyślij przez"); the store must have it. */
    public ShipmentCreationStart start(ShipmentCreationCheckRequest seed, ShipmentRequest request, Store store,
                                       Shipment placeholder, String providerName) {
        ShippingProvider provider = shippingProviders.forName(store, providerName)
                .orElseThrow(() -> new ShippingUnavailableException(store == null ? null : store.getStoreId()));
        return start(seed, request, store, placeholder, provider, providerName);
    }

    private ShipmentCreationStart start(ShipmentCreationCheckRequest seed, ShipmentRequest request, Store store,
                                        Shipment placeholder, ShippingProvider provider, String providerName) {
        String commandId = UUID.randomUUID().toString();
        ShipmentCreationCheckRequest check = seed.toBuilder()
                .commandId(commandId)
                .provider(providerName)
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
                if (ProviderErrors.isProviderAnswer(e)) {
                    return refused(owner, check, ProviderErrors.describe(e), null);
                }
                // the adapter's own words (e.g. a 5xx it gave up on before ordering): technical, so they stay here
                log.warn("Creation command {} of {} {} in store {} was refused before the provider answered: {}",
                        commandId, check.getOwnerType(), check.getOwnerId(), check.getStoreId(), e.getMessage());
                return refused(owner, check, null, NOT_CREATED_KEY);
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
            return refused(owner, check, creation.error(), null);
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

    /**
     * reason is the provider's own answer, shown as it is; reasonKey our reason for a refusal the provider never
     * answered, stored as a key so whoever opens the shipment later reads it in their own language.
     */
    private ShipmentCreationStart refused(ShipmentOwner owner, ShipmentCreationCheckRequest check, String reason,
                                          String reasonKey) {
        try {
            owner.refused(check, reason, reasonKey);
        } catch (RuntimeException e) {
            log.error("Creation command {} for {} {} in store {} (package {}) was refused ({}), but its shipment stays "
                    + "PENDING: marking it failed did not work", check.getCommandId(), check.getOwnerType(),
                    check.getOwnerId(), check.getStoreId(), check.getExternalId(), reasonKey != null ? reasonKey : reason, e);
        }
        if (reasonKey != null) {
            return ShipmentCreationStart.refused(ownMessage(reasonKey, check), false);
        }
        return ShipmentCreationStart.refused(reason, true);
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
        return ShipmentCreationStart.refused(ownMessage(UNCONFIRMED_KEY, check), false);
    }

    /** Our own reason in the operator's language, naming the integration the command went to. */
    private String ownMessage(String key, ShipmentCreationCheckRequest check) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, new Object[]{shippingIntegrationNames.of(check.getProvider(), locale)},
                locale);
    }
}
