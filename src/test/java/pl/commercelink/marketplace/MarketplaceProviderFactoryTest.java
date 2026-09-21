package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceProviderFactoryTest {

    @Mock
    private ProviderConfigurationManager configurationManager;
    @Mock
    private OAuth2CredentialStore credentialStore;
    @Mock
    private OAuth2TokenStore tokenStore;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreNotificationService notificationService;
    @Mock
    private MarketplaceProviderDescriptor descriptor;

    private MarketplaceProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MarketplaceProviderFactory(configurationManager, credentialStore, tokenStore, storesRepository,
                notificationService);
    }

    private static Store storeConnectedTo(String marketplace) {
        Store store = new Store();
        store.setStoreId("store-1");
        store.getMarketplaces().add(new MarketplaceIntegration(marketplace));
        return store;
    }

    @Test
    void flagsTheMarketplaceAsDisconnectedWithoutPublishingBeforeTheStoreIsSaved() {
        // given
        Store store = storeConnectedTo("Allegro");
        when(descriptor.name()).thenReturn("Allegro");

        // when
        factory.onAuthorizationLost(store, descriptor);

        // then
        assertThat(store.getMarketplaceIntegration("Allegro").isLoggedIn()).isFalse();
        verifyNoInteractions(notificationService);
    }

    @Test
    void publishesTheExpiredConnectionOnceTheStoreIsSaved() {
        // given
        Store store = storeConnectedTo("Allegro");
        when(descriptor.name()).thenReturn("Allegro");
        ArgumentCaptor<StoreNotification> published = ArgumentCaptor.forClass(StoreNotification.class);

        // when
        factory.afterAuthorizationLostSaved(store, descriptor);

        // then
        verify(notificationService).publish(eq("store-1"), published.capture());
        assertThat(published.getValue()).isEqualTo(new StoreNotification(StoreNotificationSeverity.WARNING,
                StoreNotificationType.UNAUTHENTICATED, "allegro_marketplace",
                "Your connection to Allegro marketplace has expired, reauthenticate it in the settings"));
    }

    /**
     * A marketplace keeps its configuration under "<name>_marketplace". When its adapter is no longer on the
     * classpath there is no descriptor to derive that name from, and the plain provider name would point at the
     * secret of a supplier or a payment gateway called the same.
     */
    @Test
    void deletesTheSecretOfAnUninstalledMarketplaceUnderItsMarketplaceName() {
        // given: no descriptor is registered, so the adapter is gone
        Store store = storeConnectedTo("Allegro");

        // when
        factory.deleteConfiguration(store, "Allegro");

        // then
        verify(configurationManager).deleteConfiguration(store, "allegro_marketplace");
        verify(configurationManager, never()).deleteConfiguration(store, "Allegro");
    }

    @Test
    void readsTheConfigurationOfAnUninstalledMarketplaceUnderItsMarketplaceName() {
        // given
        Store store = storeConnectedTo("Allegro");

        // when
        factory.loadConfiguration(store, "Allegro");

        // then
        verify(configurationManager).loadConfiguration(store, "allegro_marketplace");
    }

    @Test
    void keepsTheListenerGoingWhenTheNotificationCannotBePublished() {
        // given
        Store store = storeConnectedTo("Allegro");
        when(descriptor.name()).thenReturn("Allegro");
        doThrow(new IllegalStateException("DynamoDB unavailable")).when(notificationService).publish(eq("store-1"), any());

        // when / then
        assertThatCode(() -> factory.afterAuthorizationLostSaved(store, descriptor)).doesNotThrowAnyException();
    }
}
