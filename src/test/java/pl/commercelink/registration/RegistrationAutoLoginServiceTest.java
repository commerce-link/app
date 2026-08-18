package pl.commercelink.registration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import pl.commercelink.starter.security.model.CustomUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationAutoLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");
    private static final String POOL_ID = "eu-central-1_test";

    @Mock private CognitoIdentityProviderClient cognitoClient;
    @Mock private ClientRegistrationRepository clientRegistrationRepository;
    @Mock private OAuth2AuthorizedClientService authorizedClientService;
    @Mock private SecurityContextRepository securityContextRepository;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private RegistrationAutoLoginService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationAutoLoginService(cognitoClient, clientRegistrationRepository,
                authorizedClientService, securityContextRepository, Clock.fixed(NOW, ZoneOffset.UTC), POOL_ID);
        lenient().when(clientRegistrationRepository.findByRegistrationId("cognito")).thenReturn(registration());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("cognito")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/cognito")
                .authorizationUri("http://localhost:4566/oauth2/authorize")
                .tokenUri("http://localhost:4566/oauth2/token")
                .userInfoUri("http://localhost:4566/oauth2/userInfo")
                .userNameAttributeName("name")
                .build();
    }

    private static String idToken(String payloadJson) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
                + ".signature";
    }

    private void cognitoReturnsTokens(String payloadJson) {
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder()
                        .authenticationResult(AuthenticationResultType.builder()
                                .accessToken("access-token")
                                .idToken(idToken(payloadJson))
                                .refreshToken("refresh-token")
                                .expiresIn(3600)
                                .build())
                        .build());
    }

    @Test
    void putsAuthenticatedStoreAdminIntoTheSession() {
        // given
        cognitoReturnsTokens("{\"sub\":\"1\",\"name\":\"user@example.com\",\"email_verified\":true}");
        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);

        // when
        boolean loggedIn = service.login("user@example.com", "Tajne1!haslo", "demo-store-1", request, response);

        // then
        assertTrue(loggedIn);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), eq(request), eq(response));
        OAuth2AuthenticationToken authentication = (OAuth2AuthenticationToken) contextCaptor.getValue().getAuthentication();
        CustomUser user = (CustomUser) authentication.getPrincipal();
        assertEquals("user@example.com", authentication.getName());
        assertEquals("demo-store-1", user.getCustomAttribute("storeId").orElseThrow());
        assertEquals("ADMIN", user.getCustomAttribute("role").orElseThrow());
        assertEquals("access-token", user.accessToken().getTokenValue());
        assertEquals(Boolean.TRUE, user.getAttributes().get("email_verified"));
        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void usesAdminUserPasswordFlowWithSecretHash() {
        // given
        cognitoReturnsTokens("{\"name\":\"user@example.com\"}");
        ArgumentCaptor<AdminInitiateAuthRequest> captor = ArgumentCaptor.forClass(AdminInitiateAuthRequest.class);

        // when
        service.login("user@example.com", "Tajne1!haslo", "demo-store-1", request, response);

        // then
        verify(cognitoClient).adminInitiateAuth(captor.capture());
        AdminInitiateAuthRequest authRequest = captor.getValue();
        assertEquals(POOL_ID, authRequest.userPoolId());
        assertEquals("client-id", authRequest.clientId());
        assertEquals(AuthFlowType.ADMIN_USER_PASSWORD_AUTH, authRequest.authFlow());
        assertEquals("user@example.com", authRequest.authParameters().get("USERNAME"));
        assertEquals("Tajne1!haslo", authRequest.authParameters().get("PASSWORD"));
        assertNotNull(authRequest.authParameters().get("SECRET_HASH"));
    }

    @Test
    void storesAuthorizedClientUnderThePrincipalNameUsedByTheRefreshFilter() {
        // given
        cognitoReturnsTokens("{\"name\":\"user@example.com\"}");
        ArgumentCaptor<OAuth2AuthorizedClient> captor = ArgumentCaptor.forClass(OAuth2AuthorizedClient.class);

        // when
        service.login("user@example.com", "Tajne1!haslo", "demo-store-1", request, response);

        // then
        verify(authorizedClientService).saveAuthorizedClient(captor.capture(), any());
        OAuth2AuthorizedClient authorizedClient = captor.getValue();
        assertEquals("user@example.com", authorizedClient.getPrincipalName());
        assertEquals("access-token", authorizedClient.getAccessToken().getTokenValue());
        assertEquals("refresh-token", authorizedClient.getRefreshToken().getTokenValue());
        assertEquals(NOW.plusSeconds(3600), authorizedClient.getAccessToken().getExpiresAt());
    }

    @Test
    void fallsBackToEmailWhenIdTokenCarriesNoNameClaim() {
        // given
        cognitoReturnsTokens("{\"sub\":\"1\"}");

        // when
        boolean loggedIn = service.login("user@example.com", "Tajne1!haslo", "demo-store-1", request, response);

        // then
        assertTrue(loggedIn);
        assertEquals("user@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void reportsFailureWhenCognitoRejectsCredentials() {
        // given
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("bad password").build());

        // when
        boolean loggedIn = service.login("user@example.com", "Tajne1!haslo", "demo-store-1", request, response);

        // then
        assertFalse(loggedIn);
        verifyNoInteractions(securityContextRepository, authorizedClientService);
    }

    @Test
    void reportsFailureWhenCognitoReturnsChallengeInsteadOfTokens() {
        // given
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder().challengeName("NEW_PASSWORD_REQUIRED").build());

        // when
        boolean loggedIn = service.login("user@example.com", "Tajne1!haslo", "demo-store-1", request, response);

        // then
        assertFalse(loggedIn);
        verifyNoInteractions(securityContextRepository, authorizedClientService);
    }
}
