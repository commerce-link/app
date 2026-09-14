package pl.commercelink.marketplace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoresRepository;

@Slf4j
@Service
public class MarketplaceProviderFactory extends ProviderFactory<MarketplaceProviderDescriptor, MarketplaceProvider> {

    private final StoreNotificationService notificationService;

    public MarketplaceProviderFactory(ProviderConfigurationManager configurationManager,
                                      OAuth2CredentialStore credentialStore,
                                      OAuth2TokenStore tokenStore,
                                      StoresRepository storesRepository,
                                      StoreNotificationService notificationService) {
        super(MarketplaceProviderDescriptor.class, configurationManager,
                credentialStore, tokenStore, storesRepository);
        this.notificationService = notificationService;
    }

    @Override
    protected void onAuthorizationLost(Store store, MarketplaceProviderDescriptor descriptor) {
        store.markConnectionAsLost(descriptor.name());
    }

    @Override
    protected void afterAuthorizationLostSaved(Store store, MarketplaceProviderDescriptor descriptor) {
        try {
            notificationService.publish(store.getStoreId(), StoreNotification.marketplaceConnectionExpired(descriptor.name()));
        } catch (RuntimeException e) {
            // the marketplace tile already shows the disconnection, so a lost notification must not stop the listener
            log.error("Could not publish the expired {} connection of store {}", descriptor.name(), store.getStoreId(), e);
        }
    }

    @Override
    public String resolveCredentialName(MarketplaceProviderDescriptor descriptor) {
        return descriptor.name().toLowerCase() + "_marketplace";
    }

    public String resolveCredentialName(String marketplaceName) {
        return marketplaceName.toLowerCase() + "_marketplace";
    }
}
