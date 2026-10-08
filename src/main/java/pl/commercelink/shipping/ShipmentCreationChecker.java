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
            // the command was sent and nothing says how it ended: the provider may hold a paid label, so the
            // operator is told to check its panel before sending again, as when the result never came
            settler.failedWithKey(request, DISCONNECTED_KEY);
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
            case PENDING -> {
                if (request.getAttempt() < MAX_ATTEMPTS) {
                    publisher.publish(request.nextAttempt());
                } else {
                    settler.failedWithKey(request, UNCONFIRMED_KEY);
                }
            }
            // the command may have been sent without a package id; the check found it
            case SUCCEEDED -> settler.succeeded(request.withExternalId(result.result().externalId()), result.result());
            case FAILED -> settler.failed(request, result.error());
        }
    }

    // the integration the command was sent to; a message from before the field existed belongs to the default one
    private ShippingProvider provider(String storeId, String providerName) {
        Store store = storesRepository.findById(storeId);
        return shippingProviders.forCommand(store, providerName).orElse(null);
    }
}
