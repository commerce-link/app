package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.util.Locale;
import java.util.Optional;

/**
 * The name the operator knows a shipping integration by, for the messages about its shipments ("Integracja wysyłki
 * (Furgonetka) nie potwierdziła nadania"). A store may ship through more than one integration, so no message names one
 * itself: the name comes from here when the message is shown, never from the stored data.
 */
@Component
@RequiredArgsConstructor
public class ShippingIntegrationNames {

    static final String GENERIC_KEY = "shipping.integration.generic";

    private final ShippingProviderFactory providerFactory;
    private final MessageSource messageSource;

    /**
     * The integration of a shipment: the one that created it (provider), else the store's own for a shipment without
     * one (typed in by hand, or older than the provider field), else the generic "nieustalona" ("unknown"). A provider whose
     * adapter is no longer installed reads as generic: the store's integration is another one and would be the wrong name.
     */
    public String of(String provider, Store store, Locale locale) {
        String integration = provider != null ? provider
                : store != null ? store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER) : null;
        return displayName(integration).orElseGet(() -> messageSource.getMessage(GENERIC_KEY, null, locale));
    }

    /** The integration of a shipment whose store is not at hand: the provider's name, else the generic one. */
    public String of(String provider, Locale locale) {
        return of(provider, null, locale);
    }

    private Optional<String> displayName(String provider) {
        if (provider == null) {
            return Optional.empty();
        }
        ShippingProviderDescriptor descriptor = providerFactory.getDescriptor(provider);
        return Optional.ofNullable(descriptor).map(ShippingProviderDescriptor::displayName);
    }
}
