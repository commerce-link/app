package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserAttributeVerificationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifyUserAttributeRequest;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final String VERIFIED_SESSION_ATTRIBUTE = "emailVerified";
    static final String CODE_SENT_SESSION_ATTRIBUTE = "emailVerificationCodeSent";
    private static final String EMAIL_ATTRIBUTE = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final CognitoIdentityProviderClient cognitoClient;

    public boolean isVerified(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(VERIFIED_SESSION_ATTRIBUTE))) {
            return true;
        }
        return CustomSecurityContext.getLoggedInUser()
                .map(EmailVerificationService::verifiedInClaims)
                .orElse(true);
    }

    public void sendCode(HttpServletRequest request) {
        cognitoClient.getUserAttributeVerificationCode(GetUserAttributeVerificationCodeRequest.builder()
                .accessToken(accessToken())
                .attributeName(EMAIL_ATTRIBUTE)
                .build());
        request.getSession(true).setAttribute(CODE_SENT_SESSION_ATTRIBUTE, Boolean.TRUE);
    }

    /**
     * Sends a code unless this session already got one. A user the pool could not sign in right after registration
     * (MFA required) reaches the confirmation screen only after the hosted UI, and nothing has sent a code by then.
     */
    public boolean sendCodeOnce(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(CODE_SENT_SESSION_ATTRIBUTE))) {
            return true;
        }
        try {
            sendCode(request);
            return true;
        } catch (RuntimeException e) {
            log.error("Could not send an e-mail verification code", e);
            return false;
        }
    }

    public void verify(String code, HttpServletRequest request) {
        if (isBlank(code)) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CODE);
        }
        try {
            cognitoClient.verifyUserAttribute(VerifyUserAttributeRequest.builder()
                    .accessToken(accessToken())
                    .attributeName(EMAIL_ATTRIBUTE)
                    .code(code.trim())
                    .build());
        } catch (CodeMismatchException | ExpiredCodeException e) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CODE);
        } catch (RuntimeException e) {
            log.error("E-mail verification failed", e);
            throw new RegistrationException(RegistrationException.Reason.VERIFICATION_FAILED);
        }
        request.getSession(true).setAttribute(VERIFIED_SESSION_ATTRIBUTE, Boolean.TRUE);
    }

    private static boolean verifiedInClaims(CustomUser user) {
        Object claim = user.getAttributes().get(EMAIL_VERIFIED_CLAIM);
        return claim == null || Boolean.parseBoolean(String.valueOf(claim));
    }

    private String accessToken() {
        return CustomSecurityContext.getLoggedInUser()
                .map(user -> user.accessToken().getTokenValue())
                .orElseThrow(() -> new IllegalStateException("No logged in user to verify e-mail for"));
    }
}
