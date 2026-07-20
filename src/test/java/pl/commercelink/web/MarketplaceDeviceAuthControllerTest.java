package pl.commercelink.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2DeviceAuthorization;
import pl.commercelink.rest.client.OAuth2DeviceAuthorizationService;
import pl.commercelink.rest.client.OAuth2DeviceTokenResult;
import pl.commercelink.rest.client.OAuth2Secrets;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceDeviceAuthControllerTest {

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MarketplaceProviderFactory marketplaceProviderFactory;

    @Mock
    private OAuth2CredentialStore credentialStore;

    @Mock
    private OAuth2DeviceAuthorizationService deviceAuthorizationService;

    @Mock
    private MarketplaceProviderDescriptor descriptor;

    @Mock
    private Store store;

    private MockedStatic<CustomSecurityContext> securityStub;

    private MarketplaceDeviceAuthController controller;

    private static final AuthConfig.OAuth2 DEVICE_FLOW_CONFIG = new AuthConfig.OAuth2(
            "https://api.allegro.example", "https://auth.allegro.example/token",
            "https://auth.allegro.example/token", 7776000L,
            "application/vnd.allegro.public.v1+json", null,
            "application/vnd.allegro.public.v1+json", "https://auth.allegro.example/device");

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        controller = new MarketplaceDeviceAuthController(
                storesRepository, marketplaceProviderFactory, credentialStore, deviceAuthorizationService);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    private void stubDeviceFlowProvider() {
        when(marketplaceProviderFactory.getDescriptor("Allegro")).thenReturn(descriptor);
        when(descriptor.authConfig()).thenReturn(DEVICE_FLOW_CONFIG);
        when(marketplaceProviderFactory.resolveCredentialName("Allegro")).thenReturn("allegro_marketplace");
        when(credentialStore.getSecrets("store-1", "allegro_marketplace"))
                .thenReturn(new OAuth2Secrets("client-id", "client-secret"));
    }

    @Test
    void startReturnsVerificationDataForDeviceFlowProvider() throws Exception {
        // given
        stubDeviceFlowProvider();
        OAuth2DeviceAuthorization authorization = new ObjectMapper().readValue("""
                {"device_code":"dev-1","user_code":"ABC123",
                 "verification_uri":"https://allegro.example/skojarz",
                 "verification_uri_complete":"https://allegro.example/skojarz?code=ABC123",
                 "expires_in":3600,"interval":5}
                """, OAuth2DeviceAuthorization.class);
        when(deviceAuthorizationService.startDeviceAuthorization(
                "https://auth.allegro.example/device", "client-id", "client-secret"))
                .thenReturn(authorization);

        // when
        ResponseEntity<Map<String, Object>> response = controller.startDeviceAuthorization("Allegro");

        // then
        assertEquals(200, response.getStatusCode().value());
        assertEquals("dev-1", response.getBody().get("deviceCode"));
        assertEquals("ABC123", response.getBody().get("userCode"));
        assertEquals("https://allegro.example/skojarz?code=ABC123", response.getBody().get("verificationUriComplete"));
        assertEquals(5L, response.getBody().get("interval"));
        assertEquals(3600L, response.getBody().get("expiresIn"));
    }

    @Test
    void startRejectsProviderWithoutDeviceFlowSupport() {
        // given
        when(marketplaceProviderFactory.getDescriptor("Morele")).thenReturn(descriptor);
        when(descriptor.authConfig()).thenReturn(AuthConfig.None.INSTANCE);

        // when
        ResponseEntity<Map<String, Object>> response = controller.startDeviceAuthorization("Morele");

        // then
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody().get("error"));
        verifyNoInteractions(deviceAuthorizationService);
    }

    @Test
    void startRejectsUnknownProvider() {
        // given
        when(marketplaceProviderFactory.getDescriptor("Ghost")).thenReturn(null);

        // when / then
        assertEquals(400, controller.startDeviceAuthorization("Ghost").getStatusCode().value());
        verifyNoInteractions(deviceAuthorizationService);
    }

    @Test
    void startRejectsWhenClientCredentialsNotSavedYet() {
        // given
        when(marketplaceProviderFactory.getDescriptor("Allegro")).thenReturn(descriptor);
        when(descriptor.authConfig()).thenReturn(DEVICE_FLOW_CONFIG);
        when(marketplaceProviderFactory.resolveCredentialName("Allegro")).thenReturn("allegro_marketplace");
        when(credentialStore.getSecrets("store-1", "allegro_marketplace")).thenReturn(new OAuth2Secrets(null, null));

        // when
        ResponseEntity<Map<String, Object>> response = controller.startDeviceAuthorization("Allegro");

        // then
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(deviceAuthorizationService);
    }

    @Test
    void startRejectsWhenCredentialSecretDoesNotExistYet() {
        // given: Secrets Manager throws when the store never saved any configuration
        when(marketplaceProviderFactory.getDescriptor("Allegro")).thenReturn(descriptor);
        when(descriptor.authConfig()).thenReturn(DEVICE_FLOW_CONFIG);
        when(marketplaceProviderFactory.resolveCredentialName("Allegro")).thenReturn("allegro_marketplace");
        when(credentialStore.getSecrets("store-1", "allegro_marketplace"))
                .thenThrow(new RuntimeException("Secrets Manager can't find the specified secret"));

        // when
        ResponseEntity<Map<String, Object>> response = controller.startDeviceAuthorization("Allegro");

        // then
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody().get("error"));
        verifyNoInteractions(deviceAuthorizationService);
    }

    @Test
    void pollSeedsTokenAndConnectsIntegrationOnAuthorized() {
        // given
        stubDeviceFlowProvider();
        when(deviceAuthorizationService.pollDeviceToken(
                "https://auth.allegro.example/token", "client-id", "client-secret", "dev-1"))
                .thenReturn(new OAuth2DeviceTokenResult(
                        OAuth2DeviceTokenResult.Status.AUTHORIZED, "acc-1", "ref-1", 43199L, null));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(store.getMarketplaceIntegration("Allegro")).thenReturn(null);
        List<MarketplaceIntegration> marketplaces = new ArrayList<>();
        when(store.getMarketplaces()).thenReturn(marketplaces);

        // when
        ResponseEntity<Map<String, Object>> response = controller.pollDeviceAuthorization("Allegro", "dev-1");

        // then
        assertEquals("AUTHORIZED", response.getBody().get("status"));
        verify(marketplaceProviderFactory).seedRefreshToken(store, "Allegro", "ref-1");
        assertEquals(1, marketplaces.size());
        assertEquals("Allegro", marketplaces.get(0).getName());
        verify(storesRepository).save(store);
    }

    @Test
    void pollRestoresExistingIntegrationOnAuthorized() {
        // given
        stubDeviceFlowProvider();
        when(deviceAuthorizationService.pollDeviceToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new OAuth2DeviceTokenResult(
                        OAuth2DeviceTokenResult.Status.AUTHORIZED, "acc-1", "ref-1", 43199L, null));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(store.getMarketplaceIntegration("Allegro")).thenReturn(new MarketplaceIntegration("Allegro"));

        // when
        controller.pollDeviceAuthorization("Allegro", "dev-1");

        // then
        verify(store).markConnectionAsRestored("Allegro");
        verify(storesRepository).save(store);
    }

    @Test
    void pollPassesPendingThroughWithoutTouchingStore() {
        // given
        stubDeviceFlowProvider();
        when(deviceAuthorizationService.pollDeviceToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new OAuth2DeviceTokenResult(
                        OAuth2DeviceTokenResult.Status.PENDING, null, null, 0L, null));

        // when
        ResponseEntity<Map<String, Object>> response = controller.pollDeviceAuthorization("Allegro", "dev-1");

        // then
        assertEquals("PENDING", response.getBody().get("status"));
        verifyNoInteractions(storesRepository);
    }

    @Test
    void pollReturnsFailureReason() {
        // given
        stubDeviceFlowProvider();
        when(deviceAuthorizationService.pollDeviceToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new OAuth2DeviceTokenResult(
                        OAuth2DeviceTokenResult.Status.FAILED, null, null, 0L, "access_denied"));

        // when
        ResponseEntity<Map<String, Object>> response = controller.pollDeviceAuthorization("Allegro", "dev-1");

        // then
        assertEquals("FAILED", response.getBody().get("status"));
        assertEquals("access_denied", response.getBody().get("error"));
        verifyNoInteractions(storesRepository);
    }
}
