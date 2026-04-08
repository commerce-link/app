package pl.commercelink.shipping;

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

@Service
public class ShippingProviderFactory extends ProviderFactory<ShippingProviderDescriptor, ShippingProvider> {

    public ShippingProviderFactory(ProviderConfigurationManager configurationManager,
                                   OAuth2CredentialStore credentialStore,
                                   OAuth2TokenStore tokenStore,
                                   StoresRepository storesRepository) {
        super(ShippingProviderDescriptor.class, IntegrationType.SHIPPING_PROVIDER, configurationManager,
                credentialStore, tokenStore, storesRepository);
    }

    @Override
    protected void onAuthorizationLost(Store store, ShippingProviderDescriptor descriptor) {
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, null);
    }
}
