package pl.commercelink.shipping;

import org.springframework.stereotype.Service;
import pl.commercelink.orders.Shipment;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.util.Optional;
import java.util.Set;

/**
 * The one place that says which shipping integration handles something. A store ships through its default integration
 * (SHIPPING_PROVIDER) and additional ones (Wysyłam z Allegro); a shipment keeps the integration that created it
 * (Shipment#provider), so its label, pickup, cancellation and tracking go there, whatever the store's default is now.
 * An integration the store no longer has, or whose adapter is gone, gives no provider: callers report it instead of
 * sending the command to another account.
 */
@Service
public class ShippingProviders {

    public static final String ALLEGRO = "allegro";

    /** Integrations without a courier account of their own; never offered as the store's default. */
    public static final Set<String> ADDITIONAL_ONLY = Set.of(ALLEGRO);

    private final ShippingProviderFactory factory;

    public ShippingProviders(ShippingProviderFactory factory) {
        this.factory = factory;
    }

    public Optional<ShippingProvider> forName(Store store, String name) {
        if (store == null || !store.hasShippingIntegration(name) || !isInstalled(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(factory.get(store, name));
    }

    /** The integration that created the shipment; the default one for a shipment without (typed in, older). */
    public Optional<ShippingProvider> forShipment(Store store, Shipment shipment) {
        if (store == null) {
            return Optional.empty();
        }
        return forName(store, nameFor(store, shipment));
    }

    public Optional<ShippingProvider> defaultFor(Store store) {
        return store == null ? Optional.empty() : forName(store, store.defaultShippingIntegration());
    }

    /** A queued command names its integration; one sent before the field existed belongs to the default. */
    public Optional<ShippingProvider> forCommand(Store store, String providerOrNull) {
        if (store == null) {
            return Optional.empty();
        }
        return forName(store, providerOrNull != null ? providerOrNull : store.defaultShippingIntegration());
    }

    public String nameFor(Store store, Shipment shipment) {
        if (shipment != null && shipment.getProvider() != null) {
            return shipment.getProvider();
        }
        return store == null ? null : store.defaultShippingIntegration();
    }

    public boolean isInstalled(String name) {
        return name != null && factory.getDescriptor(name) != null;
    }

    /**
     * Drops an additional integration: its own settings and its entry. The store is saved by the caller (with the
     * rest of its change); credentials borrowed from another integration stay (ProviderFactory#deleteConfiguration).
     */
    public void disconnectAdditional(Store store, String name) {
        factory.deleteConfiguration(store, name);
        store.removeAdditionalShippingIntegration(name);
    }
}
