package pl.commercelink.registration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

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

    private static final Object ANONYMOUS = new Object();

    static Stream<Arguments> emailVerifiedClaims() {
        return Stream.of(
                Arguments.of("verified claim", Boolean.TRUE, true),
                Arguments.of("unverified claim", Boolean.FALSE, false),
                // a user without the claim is not locked out
                Arguments.of("missing claim", null, true),
                Arguments.of("anonymous request", ANONYMOUS, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emailVerifiedClaims")
    void isVerifiedFollowsTheEmailVerifiedClaim(String scenario, Object claim, boolean expected) {
        // given
        if (claim != ANONYMOUS) {
            loggedIn(claim);
        }

        // when / then
        assertEquals(expected, service().isVerified(request));
    }

    @Test
    void sendsVerificationCodeForLoggedInUser() {
        // given
        loggedIn(Boolean.FALSE);
        ArgumentCaptor<GetUserAttributeVerificationCodeRequest> captor =
                ArgumentCaptor.forClass(GetUserAttributeVerificationCodeRequest.class);

        // when
        service().sendCode(request);

        // then
        verify(cognitoClient).getUserAttributeVerificationCode(captor.capture());
        assertEquals("access-token", captor.getValue().accessToken());
        assertEquals("email", captor.getValue().attributeName());
    }

    @Test
    void codeSentOnRequestCountsAsTheCodeOfTheSession() {
        // given
        loggedIn(Boolean.FALSE);
        EmailVerificationService service = service();
        service.sendCode(request);

        // when
        boolean sent = service.sendCodeOnce(request);

        // then
        assertTrue(sent);
        verify(cognitoClient, times(1)).getUserAttributeVerificationCode(any(GetUserAttributeVerificationCodeRequest.class));
    }

    @Test
    void sendsCodeOnlyOncePerSession() {
        // given
        loggedIn(Boolean.FALSE);
        EmailVerificationService service = service();

        // when
        boolean first = service.sendCodeOnce(request);
        boolean second = service.sendCodeOnce(request);

        // then
        assertTrue(first);
        assertTrue(second);
        verify(cognitoClient, times(1)).getUserAttributeVerificationCode(any(GetUserAttributeVerificationCodeRequest.class));
    }

    @Test
    void sendsCodeAgainAfterFailedSend() {
        // given
        loggedIn(Boolean.FALSE);
        EmailVerificationService service = service();
        when(cognitoClient.getUserAttributeVerificationCode(any(GetUserAttributeVerificationCodeRequest.class)))
                .thenThrow(new RuntimeException("cognito down"))
                .thenReturn(null);

        // when
        boolean failed = service.sendCodeOnce(request);
        boolean retried = service.sendCodeOnce(request);

        // then
        assertFalse(failed);
        assertTrue(retried);
        verify(cognitoClient, times(2)).getUserAttributeVerificationCode(any(GetUserAttributeVerificationCodeRequest.class));
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
