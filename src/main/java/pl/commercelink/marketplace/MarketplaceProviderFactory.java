package pl.commercelink.marketplace;

import org.springframework.stereotype.Service;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Map;

@Service
public class MarketplaceProviderFactory extends ProviderFactory<MarketplaceProviderDescriptor, MarketplaceProvider> {

    public MarketplaceProviderFactory(ProviderConfigurationManager configurationManager,
                                      OAuth2CredentialStore credentialStore,
                                      OAuth2TokenStore tokenStore,
                                      StoresRepository storesRepository) {
        super(MarketplaceProviderDescriptor.class, configurationManager,
                credentialStore, tokenStore, storesRepository);
    }

    public MarketplaceProvider get(Store store, String marketplaceName) {
        MarketplaceProviderDescriptor descriptor = getDescriptor(marketplaceName);
        if (descriptor == null) {
            return null;
        }
        Map<String, String> config = loadConfiguration(store, marketplaceName);
        Map<String, Object> context = buildContext(store, descriptor, config);
        return descriptor.create(config, context);
    }

    @Override
    protected void onAuthorizationLost(Store store, MarketplaceProviderDescriptor descriptor) {
        store.markConnectionAsLost(descriptor.name());
    }

    @Override
    public String resolveCredentialName(MarketplaceProviderDescriptor descriptor) {
        return descriptor.name().toLowerCase() + "_marketplace";
    }

    public String resolveCredentialName(String marketplaceName) {
        return marketplaceName.toLowerCase() + "_marketplace";
    }
}
