package pl.commercelink.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2RefreshToken;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    static class NoFieldKeyDescriptor extends OAuth2Descriptor {
        @Override
        public AuthConfig authConfig() {
            return new AuthConfig.OAuth2("https://api.example.com",
                    "https://auth.example.com/token", "https://auth.example.com/token",
                    7776000L, "application/vnd.allegro.public.v1+json");
        }
    }

    private ProviderFactory<OAuth2Descriptor, Object> factoryWith(OAuth2Descriptor descriptor) {
        return new ProviderFactory<>(OAuth2Descriptor.class, null,
                configurationManager, credentialStore, tokenStore, storesRepository) {
            {
                registerDescriptor(descriptor);
            }
        };
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
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new OAuth2Descriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-123"));

        // then
        verify(tokenStore).storeToken(eq("store-1"), eq("TestOAuth"), eq("refresh_token"),
                argThat(t -> ((OAuth2RefreshToken) t).getTokenValue().equals("rt-123")));
        verify(tokenStore).deleteToken("store-1", "TestOAuth", "access_token");
    }

    @Test
    void saveConfigurationSkipsSeedingWhenRefreshTokenBlank() {
        // given
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new OAuth2Descriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", ""));

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void saveConfigurationSkipsSeedingForNonOAuth2Descriptor() {
        // given
        ProviderFactory<OAuth2Descriptor, Object> factory = factoryWith(new NoneAuthDescriptor());

        // when
        factory.saveConfiguration(store, "TestOAuth", Map.of("refreshToken", "rt-123"));

        // then
        verifyNoInteractions(tokenStore);
    }

    @Test
    void saveConfigurationSkipsSeedingWhenNoRefreshTokenFieldKeyDeclared() {
        // given
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
}
