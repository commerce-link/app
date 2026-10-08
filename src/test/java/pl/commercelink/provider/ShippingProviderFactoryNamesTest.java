package pl.commercelink.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Wysyłam z Allegro borrows the Allegro marketplace connection: its requests use the marketplace's tokens, its own
 * settings (label format) live in a secret of their own, and a lost authorisation is the marketplace's to report.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingProviderFactoryNamesTest {

    @Mock private ProviderConfigurationManager configurationManager;
    @Mock private OAuth2CredentialStore credentialStore;
    @Mock private OAuth2TokenStore tokenStore;
    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviderDescriptor allegro;
    @Mock private ShippingProviderDescriptor furgonetka;

    private ShippingProviderFactory factory;
    // package-private members of ProviderFactory are reachable only through its own type from this package
    private ProviderFactory<ShippingProviderDescriptor, ShippingProvider> base;
    private Store store;

    @BeforeEach
    void setUp() {
        when(allegro.name()).thenReturn("allegro");
        when(allegro.authConfig()).thenReturn(new AuthConfig.OAuth2("https://api.allegro.pl",
                "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token", 7776000L,
                "application/vnd.allegro.public.v1+json"));
        when(furgonetka.name()).thenReturn("furgonetka");
        when(furgonetka.authConfig()).thenReturn(AuthConfig.None.INSTANCE);
        factory = new ShippingProviderFactory(configurationManager, credentialStore, tokenStore, storesRepository);
        base = factory;
        base.registerDescriptor(allegro);
        base.registerDescriptor(furgonetka);
        store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.addAdditionalShippingIntegration("allegro");
    }

    @Test
    void allegroSettingsLiveApartFromTheMarketplaceCredentials() {
        // given
        when(configurationManager.loadConfiguration(store, "allegro")).thenReturn(Map.of("labelFormat", "ZPL"));

        // when
        Map<String, String> settings = factory.loadConfiguration(store, "allegro");
        factory.deleteConfiguration(store, "allegro");

        // then
        assertThat(settings).containsEntry("labelFormat", "ZPL");
        verify(configurationManager).deleteConfiguration(store, "allegro");
        verify(configurationManager, never()).deleteConfiguration(eq(store), eq("allegro_marketplace"));
        verifyNoInteractions(credentialStore, tokenStore);
    }

    @Test
    void anAllegroRequestIsAuthorisedWithTheMarketplaceTokens() {
        // when
        Map<String, Object> context = base.buildContext(store, allegro, factory.credentialNameFor("allegro", allegro));

        // then
        assertThat(factory.credentialNameFor("allegro", allegro)).isEqualTo("allegro_marketplace");
        assertThat(context).containsKey("restApi");
    }

    @Test
    void otherIntegrationsKeepOneNameForSettingsAndCredentials() {
        // when / then
        assertThat(factory.credentialNameFor("furgonetka", furgonetka)).isEqualTo("furgonetka");
        assertThat(factory.configurationNameFor("furgonetka", furgonetka)).isEqualTo("furgonetka");
    }

    @Test
    void lostAllegroAuthorisationLeavesTheStoreIntegrationsAsTheyAre() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        base.handleAuthorizationLost("store-1", allegro);

        // then: nothing changed, so nothing is saved (a save could only fail on a version conflict)
        assertThat(store.defaultShippingIntegration()).isEqualTo("furgonetka");
        assertThat(store.hasShippingIntegration("allegro")).isTrue();
        verify(storesRepository, never()).save(any());
    }

    @Test
    void lostCourierAuthorisationStillDisconnectsTheDefault() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        base.handleAuthorizationLost("store-1", furgonetka);

        // then
        assertThat(store.defaultShippingIntegration()).isNull();
        assertThat(store.hasShippingIntegration("allegro")).isTrue();
        verify(storesRepository).save(store);
    }
}
