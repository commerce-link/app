package pl.commercelink.registration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import pl.commercelink.starter.security.model.CustomUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserAttributeVerificationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifyUserAttributeRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private CognitoIdentityProviderClient cognitoClient;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private EmailVerificationService service() {
        return new EmailVerificationService(cognitoClient);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loggedIn(Object emailVerifiedClaim) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", "user@example.com");
        if (emailVerifiedClaim != null) {
            attributes.put("email_verified", emailVerifiedClaim);
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token", Instant.now(), Instant.now().plusSeconds(3600));
        CustomUser user = new CustomUser(new DefaultOAuth2User(List.of(), attributes, "name"), accessToken, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "cognito"));
    }

    @Test
    void treatsUserWithVerifiedClaimAsVerified() {
        // given
        loggedIn(Boolean.TRUE);

        // when / then
        assertTrue(service().isVerified(request));
    }

    @Test
    void treatsUserWithUnverifiedClaimAsUnverified() {
        // given
        loggedIn(Boolean.FALSE);

        // when / then
        assertFalse(service().isVerified(request));
    }

    @Test
    void treatsMissingClaimAsVerifiedToAvoidLockingUsersOut() {
        // given
        loggedIn(null);

        // when / then
        assertTrue(service().isVerified(request));
    }

    @Test
    void treatsAnonymousRequestAsVerified() {
        // when / then
        assertTrue(service().isVerified(request));
    }

    @Test
    void sendsVerificationCodeForLoggedInUser() {
        // given
        loggedIn(Boolean.FALSE);
        ArgumentCaptor<GetUserAttributeVerificationCodeRequest> captor =
                ArgumentCaptor.forClass(GetUserAttributeVerificationCodeRequest.class);

        // when
        service().sendCode();

        // then
        verify(cognitoClient).getUserAttributeVerificationCode(captor.capture());
        assertEquals("access-token", captor.getValue().accessToken());
        assertEquals("email", captor.getValue().attributeName());
    }

    @Test
    void swallowsSendFailureWhenSendingQuietly() {
        // given
        loggedIn(Boolean.FALSE);
        when(cognitoClient.getUserAttributeVerificationCode(any(GetUserAttributeVerificationCodeRequest.class)))
                .thenThrow(new RuntimeException("cognito down"));

        // when / then
        assertDoesNotThrow(() -> service().sendCodeQuietly("user@example.com"));
    }

    @Test
    void marksSessionVerifiedOnCorrectCode() {
        // given
        loggedIn(Boolean.FALSE);
        EmailVerificationService service = service();
        ArgumentCaptor<VerifyUserAttributeRequest> captor = ArgumentCaptor.forClass(VerifyUserAttributeRequest.class);

        // when
        service.verify(" 123456 ", request);

        // then
        verify(cognitoClient).verifyUserAttribute(captor.capture());
        assertEquals("123456", captor.getValue().code());
        assertTrue(service.isVerified(request));
    }

    @Test
    void rejectsWrongCodeAndLeavesSessionUnverified() {
        // given
        loggedIn(Boolean.FALSE);
        EmailVerificationService service = service();
        when(cognitoClient.verifyUserAttribute(any(VerifyUserAttributeRequest.class)))
                .thenThrow(CodeMismatchException.builder().message("wrong").build());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class, () -> service.verify("000000", request));
        assertEquals(RegistrationException.Reason.INVALID_CODE, e.getReason());
        assertFalse(service.isVerified(request));
    }

    @Test
    void reportsFriendlyErrorWhenCognitoCallBlowsUp() {
        // given
        loggedIn(Boolean.FALSE);
        EmailVerificationService service = service();
        when(cognitoClient.verifyUserAttribute(any(VerifyUserAttributeRequest.class)))
                .thenThrow(new RuntimeException("throttled"));

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class, () -> service.verify("123456", request));
        assertEquals(RegistrationException.Reason.VERIFICATION_FAILED, e.getReason());
        assertFalse(service.isVerified(request));
    }

    @Test
    void rejectsBlankCodeWithoutCallingCognito() {
        // given
        loggedIn(Boolean.FALSE);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class, () -> service().verify("  ", request));
        assertEquals(RegistrationException.Reason.INVALID_CODE, e.getReason());
        verifyNoInteractions(cognitoClient);
    }
}
