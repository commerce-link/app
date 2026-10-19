package pl.commercelink.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wysyłam z Allegro in the store's shipping settings. It has no login of its own: it borrows the Allegro marketplace
 * connection (ShippingProviderFactory#credentialNameFor), so it can be switched on only while that connection is
 * logged in and Allegro lets it ship (the shipments consent). Only the label format is its own setting.
 */
@Slf4j
@Component
public class AllegroShippingSettings {

    /** The marketplace whose connection Wysyłam z Allegro uses (MarketplaceProviderDescriptor#name). */
    public static final String ALLEGRO_MARKETPLACE = "Allegro";
    static final String LABEL_FORMAT = "labelFormat";

    private final ShippingProviderFactory factory;
    private final ShippingProviders shippingProviders;

    public AllegroShippingSettings(ShippingProviderFactory factory, ShippingProviders shippingProviders) {
        this.factory = factory;
        this.shippingProviders = shippingProviders;
    }

    public boolean installed() {
        return shippingProviders.isInstalled(ShippingProviders.ALLEGRO);
    }

    /** For the shipping page: what the store says, without asking Allegro. */
    public AllegroShippingSummary summary(Store store) {
        boolean enabled = store.hasShippingIntegration(ShippingProviders.ALLEGRO);
        return new AllegroShippingSummary(installed(), enabled, marketplaceConnected(store),
                enabled ? labelFormat(store) : AllegroShippingLabelFormat.PDF_A6);
    }

    /** For the Wysyłam z Allegro page: asks Allegro whether the borrowed connection may ship. */
    public AllegroShippingStatus status(Store store) {
        if (!marketplaceConnected(store)) {
            return AllegroShippingStatus.MARKETPLACE_NOT_CONNECTED;
        }
        try {
            ShippingProvider provider = factory.get(store, ShippingProviders.ALLEGRO);
            if (provider == null) {
                return AllegroShippingStatus.CHECK_FAILED;
            }
            provider.getAvailableCarriers();
        } catch (RuntimeException e) {
            if (isForbidden(e)) {
                return AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT;
            }
            log.warn("Allegro could not be asked about the shipments consent of store {}", store.getStoreId(), e);
            return AllegroShippingStatus.CHECK_FAILED;
        }
        return store.hasShippingIntegration(ShippingProviders.ALLEGRO)
                ? AllegroShippingStatus.ENABLED : AllegroShippingStatus.READY;
    }

    public AllegroShippingLabelFormat labelFormat(Store store) {
        Map<String, String> configuration = factory.loadConfiguration(store, ShippingProviders.ALLEGRO);
        return AllegroShippingLabelFormat.of(configuration == null ? null : configuration.get(LABEL_FORMAT));
    }

    /** Saves the label format and switches the integration on; the caller saves the store. */
    public void enable(Store store, AllegroShippingLabelFormat labelFormat) {
        AllegroShippingStatus status = status(store);
        if (status != AllegroShippingStatus.READY && status != AllegroShippingStatus.ENABLED) {
            throw new IllegalStateException("Wysyłam z Allegro cannot be enabled while " + status);
        }
        factory.saveConfiguration(store, ShippingProviders.ALLEGRO, Map.of(LABEL_FORMAT, labelFormat.name()));
        store.addAdditionalShippingIntegration(ShippingProviders.ALLEGRO);
    }

    /** Switches the integration off; created shipments stay, their actions report the integration as gone. */
    public void disable(Store store) {
        shippingProviders.disconnectAdditional(store, ShippingProviders.ALLEGRO);
    }

    private static boolean marketplaceConnected(Store store) {
        return store.hasActiveMarketplaceIntegration(ALLEGRO_MARKETPLACE);
    }

    private static boolean isForbidden(Throwable e) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = e; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof HttpClientException http && http.getStatusCode() == 403) {
                return true;
            }
        }
        return false;
    }

    public record AllegroShippingSummary(boolean installed, boolean enabled, boolean marketplaceConnected,
                                         AllegroShippingLabelFormat labelFormat) {
    }
}
