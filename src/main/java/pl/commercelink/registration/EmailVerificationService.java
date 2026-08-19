package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserAttributeVerificationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifyUserAttributeRequest;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final String VERIFIED_SESSION_ATTRIBUTE = "emailVerified";
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

    public void sendCode() {
        cognitoClient.getUserAttributeVerificationCode(GetUserAttributeVerificationCodeRequest.builder()
                .accessToken(accessToken())
                .attributeName(EMAIL_ATTRIBUTE)
                .build());
    }

    public void sendCodeQuietly(String email) {
        try {
            sendCode();
        } catch (RuntimeException e) {
            System.err.println("[Registration] Could not send verification code to " + email + ": " + e.getMessage());
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
            System.err.println("[Registration] E-mail verification failed: " + e.getMessage());
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
