package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Slf4j
@Service
public class ShippingProviderFactory extends ProviderFactory<ShippingProviderDescriptor, ShippingProvider> {

    /** Wysyłam z Allegro signs its requests with the store's Allegro marketplace connection. */
    static final String ALLEGRO_CREDENTIALS = "allegro_marketplace";

    public ShippingProviderFactory(ProviderConfigurationManager configurationManager,
                                   OAuth2CredentialStore credentialStore,
                                   OAuth2TokenStore tokenStore,
                                   StoresRepository storesRepository) {
        super(ShippingProviderDescriptor.class, IntegrationType.SHIPPING_PROVIDER, configurationManager,
                credentialStore, tokenStore, storesRepository);
    }

    @Override
    public String credentialNameFor(String providerName, ShippingProviderDescriptor descriptor) {
        return ShippingProviders.ALLEGRO.equals(providerName) ? ALLEGRO_CREDENTIALS
                : super.credentialNameFor(providerName, descriptor);
    }

    @Override
    public String configurationNameFor(String providerName, ShippingProviderDescriptor descriptor) {
        // the label format lives in "{storeId}-allegro"; the marketplace secret is never written from here
        return ShippingProviders.ALLEGRO.equals(providerName) ? ShippingProviders.ALLEGRO
                : super.configurationNameFor(providerName, descriptor);
    }

    @Override
    protected boolean onAuthorizationLost(Store store, ShippingProviderDescriptor descriptor) {
        if (ShippingProviders.ALLEGRO.equals(descriptor.name())) {
            // the tokens are the marketplace's: its own factory marks the connection lost and tells the store
            log.warn("Allegro refused the marketplace tokens of store {} for a shipping call", store.getStoreId());
            return false;
        }
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, null);
        return true;
    }
}
