package pl.commercelink.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2DeviceAuthorization;
import pl.commercelink.rest.client.OAuth2DeviceAuthorizationService;
import pl.commercelink.rest.client.OAuth2DeviceTokenResult;
import pl.commercelink.rest.client.OAuth2Secrets;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.MarketplaceAuthorization.AuthorizationException;
import pl.commercelink.web.MarketplaceAuthorization.Pending;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceAuthorizationTest {

    private static final AuthConfig.OAuth2 DEVICE_FLOW = new AuthConfig.OAuth2(
            "https://api.allegro.example", "https://auth.allegro.example/token",
            "https://auth.allegro.example/token", 7776000L,
            "application/vnd.allegro.public.v1+json", null,
            "application/vnd.allegro.public.v1+json", "https://auth.allegro.example/device");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private OAuth2CredentialStore credentialStore;
    @Mock
    private StoreNotificationService notificationService;
    @Mock
    private OAuth2DeviceAuthorizationService deviceAuthorizationService;
    @Mock
    private MarketplaceProviderDescriptor allegro;

    private MarketplaceAuthorization authorization;
    private Store store;

    @BeforeEach
    void setUp() {
        authorization = new MarketplaceAuthorization(storesRepository, providerFactory, credentialStore,
                notificationService, deviceAuthorizationService);
        store = new Store();
        store.setStoreId("store-9");
        when(providerFactory.getDescriptor("Allegro")).thenReturn(allegro);
        when(allegro.authConfig()).thenReturn(DEVICE_FLOW);
        when(providerFactory.resolveCredentialName("Allegro")).thenReturn("allegro_marketplace");
        when(credentialStore.getSecrets("store-9", "allegro_marketplace")).thenReturn(new OAuth2Secrets("client-id", "client-secret"));
    }

    @Test
    void onlyAMarketplaceWithADeviceFlowIsConnectedOnItsOwnPage() {
        // given
        MarketplaceProviderDescriptor ceneo = mock(MarketplaceProviderDescriptor.class);
        when(providerFactory.getDescriptor("Ceneo")).thenReturn(ceneo);

        // when / then
        assertThat(authorization.supports("Allegro")).isTrue();
        assertThat(authorization.supports("Ceneo")).isFalse();
        assertThat(authorization.supports("Unknown")).isFalse();
    }

    /** The credentials come from the store given, not from the session: a super admin connects the store they look at. */
    @Test
    void startingAsksTheMarketplaceForACodeWithTheStoresOwnCredentials() throws Exception {
        // given
        when(deviceAuthorizationService.startDeviceAuthorization("https://auth.allegro.example/device", "client-id", "client-secret"))
                .thenReturn(new ObjectMapper().readValue("""
                        {"device_code":"dev-1","user_code":"ABC123",
                         "verification_uri":"https://allegro.example/skojarz",
                         "verification_uri_complete":"https://allegro.example/skojarz?code=ABC123",
                         "expires_in":3600,"interval":5}
                        """, OAuth2DeviceAuthorization.class));

        // when
        Pending pending = authorization.start(store, "Allegro");

        // then
        assertThat(pending.storeId()).isEqualTo("store-9");
        assertThat(pending.deviceCode()).isEqualTo("dev-1");
        assertThat(pending.userCode()).isEqualTo("ABC123");
        assertThat(pending.openUri()).isEqualTo("https://allegro.example/skojarz?code=ABC123");
        assertThat(pending.intervalSeconds()).isEqualTo(5);
        assertThat(pending.expiresAt()).isAfter(Instant.now().plusSeconds(3500));
        assertThat(pending.belongsTo("store-9", "Allegro")).isTrue();
        assertThat(pending.belongsTo("store-1", "Allegro")).isFalse();
    }

    @Test
    void startingWithoutSavedCredentialsSaysSo() {
        // given
        when(credentialStore.getSecrets("store-9", "allegro_marketplace")).thenThrow(new RuntimeException("no secret"));

        // when / then
        assertThatThrownBy(() -> authorization.start(store, "Allegro"))
                .isInstanceOfSatisfying(AuthorizationException.class,
                        e -> assertThat(e.messageKey()).isEqualTo("store.marketplace.authorize.error.noCredentials"));
    }

    @Test
    void credentialsTheMarketplaceRejectsAreReportedAsSuch() {
        // given
        when(deviceAuthorizationService.startDeviceAuthorization(anyString(), anyString(), anyString()))
                .thenThrow(new HttpClientException(401, "{\"error\":\"invalid_client\"}"));

        // when / then
        assertThatThrownBy(() -> authorization.start(store, "Allegro"))
                .isInstanceOfSatisfying(AuthorizationException.class,
                        e -> assertThat(e.messageKey()).isEqualTo("store.marketplace.authorize.error.rejected"));
    }

    @Test
    void aConfirmedCodeStoresTheTokenMarksTheMarketplaceConnectedAndClearsTheWarning() {
        // given
        store.connectMarketplace("Allegro", true);
        when(deviceAuthorizationService.pollDeviceToken("https://auth.allegro.example/token", "client-id", "client-secret", "dev-1"))
                .thenReturn(new OAuth2DeviceTokenResult(OAuth2DeviceTokenResult.Status.AUTHORIZED, "acc-1", "ref-1", 43199L, null));

        // when
        OAuth2DeviceTokenResult.Status status = authorization.check(store, pending());

        // then
        assertThat(status).isEqualTo(OAuth2DeviceTokenResult.Status.AUTHORIZED);
        verify(providerFactory).seedRefreshToken(store, "Allegro", "ref-1");
        assertThat(store.getMarketplaceIntegration("Allegro").isLoggedIn()).isTrue();
        verify(storesRepository).save(store);
        verify(notificationService).resolve("store-9", StoreNotificationType.UNAUTHENTICATED, "allegro_marketplace");
    }

    @Test
    void aConfirmedCodeForAMarketplaceRemovedMeanwhileConnectsItAgain() {
        // given
        when(deviceAuthorizationService.pollDeviceToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new OAuth2DeviceTokenResult(OAuth2DeviceTokenResult.Status.AUTHORIZED, "acc-1", "ref-1", 43199L, null));

        // when
        authorization.check(store, pending());

        // then
        assertThat(store.getMarketplaces()).extracting(MarketplaceIntegration::getName).containsExactly("Allegro");
    }

    @Test
    void aCodeNotConfirmedYetLeavesTheStoreAlone() {
        // given
        when(deviceAuthorizationService.pollDeviceToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new OAuth2DeviceTokenResult(OAuth2DeviceTokenResult.Status.PENDING, null, null, 0L, null));

        // when
        OAuth2DeviceTokenResult.Status status = authorization.check(store, pending());

        // then
        assertThat(status).isEqualTo(OAuth2DeviceTokenResult.Status.PENDING);
        verifyNoInteractions(storesRepository, notificationService);
    }

    private static Pending pending() {
        return new Pending("store-9", "Allegro", "dev-1", "ABC123", "https://allegro.example/skojarz", null,
                Instant.now().plusSeconds(600), 5);
    }
}
