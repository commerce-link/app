package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.shipping.api.PickupOrder;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;


/**
 * Reads the result of a pickup command sent by {@link ShipmentPickupService}. A result still being computed is asked
 * for again with a new delayed message (never by sleeping); the last attempt always leaves the pickups in a final
 * state. The writes themselves are {@link ShipmentPickupSettler}'s, which ignores a command its packages no longer
 * wait for.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentPickupChecker {

    static final int MAX_ATTEMPTS = 6;
    static final String UNCONFIRMED_KEY = ShipmentPickupService.UNCONFIRMED_KEY;
    static final String DISCONNECTED_KEY = ShipmentPickup.UNCONFIRMED_DISCONNECTED_KEY;

    private final StoresRepository storesRepository;
    private final ShippingProviders shippingProviders;
    private final ShipmentPickupEventPublisher publisher;
    private final ShipmentPickupSettler settler;

    public void check(ShipmentPickupCheckRequest request) {
        ShippingProvider provider = provider(request.getStoreId(), request.getProvider());
        if (provider == null) {
            // the integration may be back before the checks run out; only the last attempt settles the command, as
            // unconfirmed: it was sent and its outcome is unknown, so the courier may still come
            log.warn("Pickup check without its integration store={} provider={} command={} attempt={}",
                    request.getStoreId(), request.getProvider(), request.getCommandId(), request.getAttempt());
            askAgainOrSettle(request, DISCONNECTED_KEY);
            return;
        }
        PickupOrder result;
        try {
            result = provider.checkPickupOrder(request.getCommandId());
        } catch (RuntimeException e) {
            log.warn("Pickup check failed store={} command={} attempt={}: {}", request.getStoreId(),
                    request.getCommandId(), request.getAttempt(), e.getMessage(), e);
            result = PickupOrder.pending(request.getCommandId());
        }
        switch (result.status()) {
            case PENDING -> askAgainOrSettle(request, UNCONFIRMED_KEY);
            // the provider may book only some of the packages; an empty list names none, so it covers them all
            case SUCCEEDED -> {
                if (result.externalIds() == null || result.externalIds().isEmpty()) {
                    settler.ordered(request, result.pickupId());
                } else {
                    settler.ordered(request, result.pickupId(), result.externalIds());
                }
            }
            case FAILED -> settler.failed(request, result.error());
        }
    }

    private void askAgainOrSettle(ShipmentPickupCheckRequest request, String unconfirmedKey) {
        if (request.getAttempt() < MAX_ATTEMPTS) {
            publisher.publish(request.nextAttempt());
        } else {
            settler.failedWithKey(request, unconfirmedKey);
        }
    }

    // the integration the command was sent to; a message from before the field existed belongs to the default one
    private ShippingProvider provider(String storeId, String providerName) {
        Store store = storesRepository.findById(storeId);
        return shippingProviders.forCommand(store, providerName).orElse(null);
    }
}
