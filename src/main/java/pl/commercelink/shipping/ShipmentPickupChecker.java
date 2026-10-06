package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.shipping.api.PickupOrder;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

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
    static final String NO_PROVIDER_KEY = "shipping.pickup.no.provider";

    private final StoresRepository storesRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final ShipmentPickupEventPublisher publisher;
    private final ShipmentPickupSettler settler;

    public void check(ShipmentPickupCheckRequest request) {
        ShippingProvider provider = provider(request.getStoreId());
        if (provider == null) {
            settler.failedWithKey(request, NO_PROVIDER_KEY);
            return;
        }
        PickupOrder result;
        try {
            result = provider.checkPickupOrder(request.getCommandId());
        } catch (RuntimeException e) {
            log.warn("Pickup check failed store={} command={} attempt={}: {}", request.getStoreId(),
                    request.getCommandId(), request.getAttempt(), e.getMessage(), e);
            result = PickupOrder.pending(request.getCommandId(), List.of(), null);
        }
        switch (result.status()) {
            case PENDING -> {
                if (request.getAttempt() < MAX_ATTEMPTS) {
                    publisher.publish(request.nextAttempt());
                } else {
                    settler.failedWithKey(request, UNCONFIRMED_KEY);
                }
            }
            case SUCCEEDED -> settler.ordered(request, result.pickupId());
            case FAILED -> settler.failed(request, result.error());
        }
    }

    private ShippingProvider provider(String storeId) {
        Store store = storesRepository.findById(storeId);
        return store == null ? null : shippingProviderFactory.get(store);
    }
}
