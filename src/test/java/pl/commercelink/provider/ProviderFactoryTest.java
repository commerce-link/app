package pl.commercelink.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.rest.client.ConfigurableOAuth2AuthorizationService;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2RefreshToken;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.rest.client.RestApi;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderFactoryTest {

    @Mock
    private ProviderConfigurationManager configurationManager;

    @Mock
    private OAuth2CredentialStore credentialStore;

    @Mock
    private OAuth2TokenStore tokenStore;

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private Store store;

    static class OAuth2Descriptor implements ProviderDescriptor<Object> {
        @Override
        public String name() {
            return "TestOAuth";
        }

        @Override
        public String displayName() {
            return "TestOAuth";
        }

        @Override
        public List<ProviderField> configurationFields() {
            return List.of();
        }

        @Override
        public Object create(Map<String, String> configuration) {
            return new Object();
        }

        @Override
        public AuthConfig authConfig() {
            return new AuthConfig.OAuth2("https://api.example.com",
                    "https://auth.example.com/token", "https://auth.example.com/token",
                    7776000L, "application/vnd.allegro.public.v1+json", "refreshToken");
        }
    }

    static class NoneAuthDescriptor extends OAuth2Descriptor {
        @Override
        public AuthConfig authConfig() {
            return AuthConfig.None.INSTANCE;
        }
    }

    static class OAuth2WithContentTypeDescriptor extends OAuth2Descriptor {
        @Override
        public AuthConfig authConfig() {
            return new AuthConfig.OAuth2("https://api.example.com",
                    "https://auth.example.com/token", "https://auth.example.com/token",
                    7776000L, "application/vnd.allegro.public.v1+json", "refreshToken",
                    "application/vnd.allegro.public.v1+json");
        }
    }

    static class DeviceFlowDescriptor extends OAuth2Descriptor {
        @Override
        public String name() {
            return "TestDevice";
        }

        @Override
        public String displayName() {
            return "TestDevice";
        }

        @Override
        public AuthConfig authConfig() {
            return new AuthConfig.OAuth2("https://api.example.com",
                    "https://auth.example.com/token", "https://auth.example.com/token",
                    7776000L, "application/vnd.allegro.public.v1+json", null,
                    "application/vnd.allegro.public.v1+json", "https://auth.example.com/device");
        }
    }

    static class NoFieldKeyDescriptor extends OAuth2Descriptor {
        @Override
        public AuthConfig authConfig() {
            return new AuthConfig.OAuth2("https://api.example.com",
                    "https://auth.example.com/token", "https://auth.example.com/token",
                    7776000L, "application/vnd.allegro.public.v1+json");
        }
    }

    private ProviderFactory<OAuth2Descriptor, Object> factoryWith(OAuth2Descriptor descriptor) {
        ProviderFactory<OAuth2Descriptor, Object> factory = new ProviderFactory<>(OAuth2Descriptor.class, null,
                configurationManager, credentialStore, tokenStore, storesRepository);
        factory.registerDescriptor(descriptor);
        return factory;
    }

    @Test
    void seedRefreshTokenDirectlyStoresTokenAndEvictsAccessToken() {
        // given
        when(store.getStoreId()).thenReturn("store-1");
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new DeviceFlowDescriptor());

        // when
        factory.seedRefreshToken(store, "TestDevice", "rt-device-1");

        // then
        verify(tokenStore).storeToken(eq("store-1"), eq("TestDevice"), eq("refresh_token"),
                argThat(t -> ((OAuth2RefreshToken) t).getTokenValue().equals("rt-device-1")
                        && ((OAuth2RefreshToken) t).getExpiresAt().getEpochSecond()
                                - ((OAuth2RefreshToken) t).getIssuedAt().getEpochSecond() == 7776000L));
        verify(tokenStore).deleteToken("store-1", "TestDevice", "access_token");
    }

    @Test
    void seedRefreshTokenDirectlyIgnoresBlankTokenAndUnknownProvider() {
        // given
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new DeviceFlowDescriptor());

        // when
        factory.seedRefreshToken(store, "TestDevice", " ");
        factory.seedRefreshToken(store, "Unknown", "rt-1");

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void seedRefreshTokenDirectlySkipsNonOAuth2Descriptor() {
        // given
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new NoneAuthDescriptor());

        // when
        factory.seedRefreshToken(store, "TestOAuth", "rt-1");

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void deviceAuthProvidersListsOnlyDescriptorsDeclaringDeviceUrl() {
        // given
        ProviderFactory<OAuth2Descriptor, Object> factory = new ProviderFactory<>(OAuth2Descriptor.class, null,
                configurationManager, credentialStore, tokenStore, storesRepository);
        factory.registerDescriptor(new OAuth2Descriptor());
        factory.registerDescriptor(new DeviceFlowDescriptor());

        // when / then
        assertEquals(List.of("TestDevice"), factory.deviceAuthProviders());
    }

    @Test
    void resolveAuthEndpointPrefixesRelativePathWithApiUrl() {
        // when
        String resolved = ProviderFactory.resolveAuthEndpoint("https://api-marketplace.morele.net", "/auth/refresh");

        // then
        assertEquals("https://api-marketplace.morele.net/auth/refresh", resolved);
    }

    @Test
    void resolveAuthEndpointKeepsAbsoluteUrl() {
        // when
        String resolved = ProviderFactory.resolveAuthEndpoint(
                "https://api.allegro.pl", "https://allegro.pl/auth/oauth/token");

        // then
        assertEquals("https://allegro.pl/auth/oauth/token", resolved);
    }

    @Test
    void saveConfigurationSeedsRefreshTokenForOAuth2Descriptor() {
        // given
        when(store.getStoreId()).thenReturn("store-1");
        when(configurationManager.saveConfiguration(any(), anyString(), any(), anyMap())).thenReturn(true);
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new OAuth2Descriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-123"));

        // then
        verify(tokenStore).storeToken(eq("store-1"), eq("TestOAuth"), eq("refresh_token"),
                argThat(t -> ((OAuth2RefreshToken) t).getTokenValue().equals("rt-123")
                        && ((OAuth2RefreshToken) t).getExpiresAt().getEpochSecond()
                                - ((OAuth2RefreshToken) t).getIssuedAt().getEpochSecond() == 7776000L));
        verify(tokenStore).deleteToken("store-1", "TestOAuth", "access_token");
    }

    @Test
    void saveConfigurationSkipsSeedingWhenConfigurationNotPersisted() {
        // given
        when(configurationManager.saveConfiguration(any(), anyString(), any(), anyMap())).thenReturn(false);
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new OAuth2Descriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-1"));

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void saveConfigurationSkipsSeedingWhenRefreshTokenBlank() {
        // given
        when(configurationManager.saveConfiguration(any(), anyString(), any(), anyMap())).thenReturn(true);
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new OAuth2Descriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", ""));

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void saveConfigurationSkipsSeedingForNonOAuth2Descriptor() {
        // given
        when(configurationManager.saveConfiguration(any(), anyString(), any(), anyMap())).thenReturn(true);
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new NoneAuthDescriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-123"));

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void saveConfigurationSkipsSeedingWhenNoRefreshTokenFieldKeyDeclared() {
        // given
        when(configurationManager.saveConfiguration(any(), anyString(), any(), anyMap())).thenReturn(true);
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new NoFieldKeyDescriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-123"));

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void oauth2DefaultHeadersSetsAcceptOnly() {
        // given
        AuthConfig.OAuth2 oauth2 = new AuthConfig.OAuth2(
                "https://api.example.com", "/auth", "/refresh", 100L, "application/vnd.morele+json");

        // when
        Map<String, String> headers = ProviderFactory.oauth2DefaultHeaders(oauth2);

        // then
        assertEquals(Map.of("Accept", "application/vnd.morele+json"), headers);
    }

    @Test
    void oauth2DefaultHeadersSetsContentTypeWhenDeclared() {
        // given
        AuthConfig.OAuth2 oauth2 = new AuthConfig.OAuth2(
                "https://api.example.com", "/auth", "/refresh", 100L,
                "application/vnd.allegro.public.v1+json", "refreshToken", "application/vnd.allegro.public.v1+json");

        // when
        Map<String, String> headers = ProviderFactory.oauth2DefaultHeaders(oauth2);

        // then
        assertEquals("application/vnd.allegro.public.v1+json", headers.get("Accept"));
        assertEquals("application/vnd.allegro.public.v1+json", headers.get("Content-Type"));
    }

    @Test
    void oauth2DefaultHeadersEmptyWhenNothingDeclared() {
        // given
        AuthConfig.OAuth2 oauth2 = AuthConfig.OAuth2.of("https://api.example.com", "/auth", "/refresh", 100L);

        // when / then
        assertTrue(ProviderFactory.oauth2DefaultHeaders(oauth2).isEmpty());
    }

    @Test
    void buildContextWiresAcceptAndContentTypeHeadersOntoRestApi() throws Exception {
        // given
        OAuth2WithContentTypeDescriptor descriptor = new OAuth2WithContentTypeDescriptor();
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(descriptor);

        // when
        Map<String, Object> context = factory.buildContext(store, descriptor);

        // then
        assertTrue(context.get("restApi") instanceof RestApiWithRetry);
        Map<String, String> headers = defaultHeadersOf((RestApiWithRetry) context.get("restApi"));
        assertEquals("application/vnd.allegro.public.v1+json", headers.get("Accept"));
        assertEquals("application/vnd.allegro.public.v1+json", headers.get("Content-Type"));
    }

    private static Map<String, String> defaultHeadersOf(RestApiWithRetry restApiWithRetry) throws Exception {
        Field restApiField = RestApiWithRetry.class.getDeclaredField("restApi");
        restApiField.setAccessible(true);
        RestApi restApi = (RestApi) restApiField.get(restApiWithRetry);

        Field defaultHeadersField = RestApi.class.getDeclaredField("defaultHeaders");
        defaultHeadersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) defaultHeadersField.get(restApi);
        return headers;
    }

    @Test
    void createAuthServiceResolvesRelativeAuthAndAbsoluteRefreshEndpoints() throws Exception {
        // given
        OAuth2Descriptor descriptor = new OAuth2Descriptor();
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(descriptor);
        AuthConfig.OAuth2 oauth2 = new AuthConfig.OAuth2(
                "https://api.example.com", "/auth/register", "https://auth.example.com/token",
                7776000L, "application/vnd.allegro.public.v1+json", "refreshToken");

        // when
        ConfigurableOAuth2AuthorizationService authService = factory.createAuthService(store, descriptor, oauth2);

        // then
        String[] endpoints = authEndpointsOf(authService);
        assertEquals("https://api.example.com/auth/register", endpoints[0]);
        assertEquals("https://auth.example.com/token", endpoints[1]);
    }

    @Test
    void seedsRefreshTokenUnderProviderSpecificCredentialName() {
        // given
        when(store.getStoreId()).thenReturn("store-1");
        when(configurationManager.saveConfiguration(any(), anyString(), any(), anyMap())).thenReturn(true);
        OAuth2Descriptor descriptor = new OAuth2Descriptor();
        ProviderFactory<OAuth2Descriptor, Object> factory = new ProviderFactory<>(OAuth2Descriptor.class, null,
                configurationManager, credentialStore, tokenStore, storesRepository) {
            @Override
            protected String resolveCredentialName(OAuth2Descriptor descriptor) {
                return "testoauth_marketplace";
            }
        };
        factory.registerDescriptor(descriptor);

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-1"));

        // then
        verify(tokenStore).storeToken(eq("store-1"), eq("testoauth_marketplace"),
                eq(ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN), any());
        verify(configurationManager).saveConfiguration(any(), eq("testoauth_marketplace"), any(), anyMap());
    }

    private static String[] authEndpointsOf(ConfigurableOAuth2AuthorizationService authService) throws Exception {
        Field authorizationEndpointField = ConfigurableOAuth2AuthorizationService.class
                .getDeclaredField("authorizationEndpoint");
        authorizationEndpointField.setAccessible(true);

        Field refreshTokenEndpointField = ConfigurableOAuth2AuthorizationService.class
                .getDeclaredField("refreshTokenEndpoint");
        refreshTokenEndpointField.setAccessible(true);

        return new String[] {
                (String) authorizationEndpointField.get(authService),
                (String) refreshTokenEndpointField.get(authService)
        };
    }
}
