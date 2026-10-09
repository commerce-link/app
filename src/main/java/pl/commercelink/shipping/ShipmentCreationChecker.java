package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.shipping.api.ShipmentCreation;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

/**
 * Reads the result of a creation command sent by {@link ShipmentCreationService}. A result still being computed is
 * asked for again with a new delayed message (never by sleeping); the last attempt always leaves the shipment in a
 * final state. The writes themselves are {@link ShipmentCreationSettler}'s.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentCreationChecker {

    static final int MAX_ATTEMPTS = 8;
    static final String UNCONFIRMED_KEY = ShipmentCreationState.UNCONFIRMED_KEY;
    static final String DISCONNECTED_KEY = ShipmentCreationState.UNCONFIRMED_DISCONNECTED_KEY;

    private final StoresRepository storesRepository;
    private final ShippingProviders shippingProviders;
    private final ShipmentOwners owners;
    private final ShipmentCreationEventPublisher publisher;
    private final ShipmentCreationSettler settler;

    public void check(ShipmentCreationCheckRequest request) {
        if (!owners.get(request.getOwnerType()).awaits(request)) {
            log.warn("Creation check dropped, nothing waits for it: store={} owner={} {} command={}",
                    request.getStoreId(), request.getOwnerType(), request.getOwnerId(), request.getCommandId());
            return;
        }
        ShippingProvider provider = provider(request.getStoreId(), request.getProvider());
        if (provider == null) {
            // the integration may be back (reconnected, adapter redeployed) before the checks run out, and the command
            // may have ended by then; only the last attempt settles it, as unconfirmed: the provider may hold a paid
            // label, so the operator is told to check its panel before sending again
            log.warn("Creation check without its integration store={} provider={} command={} attempt={}",
                    request.getStoreId(), request.getProvider(), request.getCommandId(), request.getAttempt());
            askAgainOrSettle(request, DISCONNECTED_KEY);
            return;
        }
        ShipmentCreation result;
        try {
            result = provider.checkShipmentCreation(request.getCommandId(), request.getExternalId());
        } catch (RuntimeException e) {
            log.warn("Creation check failed store={} command={} attempt={}: {}", request.getStoreId(),
                    request.getCommandId(), request.getAttempt(), e.getMessage(), e);
            result = ShipmentCreation.pending(request.getCommandId(), request.getExternalId());
        }
        switch (result.status()) {
            case PENDING -> askAgainOrSettle(withPackageId(request, result.externalId()), UNCONFIRMED_KEY);
            // the command may have been sent without a package id; the check found it
            case SUCCEEDED -> settler.succeeded(request.withExternalId(result.result().externalId()), result.result());
            case FAILED -> settler.failed(request, result.error());
        }
    }

    private void askAgainOrSettle(ShipmentCreationCheckRequest request, String unconfirmedKey) {
        if (request.getAttempt() < MAX_ATTEMPTS) {
            publisher.publish(request.nextAttempt());
        } else {
            settler.failedWithKey(request, unconfirmedKey);
        }
    }

    /**
     * A provider may name its shipment before the command ends (Wysyłam z Allegro: SUCCESS before the waybill). The id
     * goes onto the owner and into the next message, so a command never confirmed still points at the shipment the
     * provider holds and a later check (ShipmentCreationReconciler) can find it.
     */
    private ShipmentCreationCheckRequest withPackageId(ShipmentCreationCheckRequest request, String externalId) {
        if (externalId == null || externalId.equals(request.getExternalId())) {
            return request;
        }
        ShipmentCreationCheckRequest known = request.withExternalId(externalId);
        try {
            owners.get(known.getOwnerType()).recordExternalId(known);
            return known;
        } catch (RuntimeException e) {
            // the next message goes without the id, so the next attempt, given it again, records it again
            log.warn("Package {} of creation command {} for {} {} in store {} was not recorded on its owner",
                    externalId, known.getCommandId(), known.getOwnerType(), known.getOwnerId(), known.getStoreId(), e);
            return request;
        }
    }

    // the integration the command was sent to; a message from before the field existed belongs to the default one
    private ShippingProvider provider(String storeId, String providerName) {
        Store store = storesRepository.findById(storeId);
        return shippingProviders.forCommand(store, providerName).orElse(null);
    }
}
