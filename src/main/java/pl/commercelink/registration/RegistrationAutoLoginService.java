package pl.commercelink.registration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.starter.security.model.CustomUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.registration.enabled", havingValue = "true")
public class RegistrationAutoLoginService {

    private static final String REGISTRATION_ID = "cognito";
    private static final String NAME_CLAIM = "name";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final CognitoIdentityProviderClient cognitoClient;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;
    private final String userPoolId;

    @Autowired
    public RegistrationAutoLoginService(CognitoIdentityProviderClient cognitoClient,
                                        ClientRegistrationRepository clientRegistrationRepository,
                                        OAuth2AuthorizedClientService authorizedClientService,
                                        @Value("${cognito.user-pool-id}") String userPoolId) {
        this(cognitoClient, clientRegistrationRepository, authorizedClientService,
                new HttpSessionSecurityContextRepository(), Clock.systemUTC(), userPoolId);
    }

    RegistrationAutoLoginService(CognitoIdentityProviderClient cognitoClient,
                                 ClientRegistrationRepository clientRegistrationRepository,
                                 OAuth2AuthorizedClientService authorizedClientService,
                                 SecurityContextRepository securityContextRepository,
                                 Clock clock,
                                 String userPoolId) {
        this.cognitoClient = cognitoClient;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizedClientService = authorizedClientService;
        this.securityContextRepository = securityContextRepository;
        this.clock = clock;
        this.userPoolId = userPoolId;
    }

    public boolean login(String email, String password, String storeId,
                         HttpServletRequest request, HttpServletResponse response) {
        try {
            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
            AuthenticationResultType tokens = authenticate(email, password, registration);
            if (tokens == null) {
                System.err.println("[Registration] Auto login for " + email + " returned no tokens");
                return false;
            }
            establishSession(email, storeId, tokens, registration, request, response);
            return true;
        } catch (RuntimeException e) {
            System.err.println("[Registration] Auto login failed for " + email + ": " + e.getMessage());
            return false;
        }
    }

    private AuthenticationResultType authenticate(String email, String password, ClientRegistration registration) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("USERNAME", email);
        parameters.put("PASSWORD", password);
        parameters.put("SECRET_HASH", secretHash(email, registration));

        AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                .userPoolId(userPoolId)
                .clientId(registration.getClientId())
                .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                .authParameters(parameters)
                .build());

        return authResponse.authenticationResult();
    }

    private void establishSession(String email, String storeId, AuthenticationResultType tokens,
                                  ClientRegistration registration,
                                  HttpServletRequest request, HttpServletResponse response) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(tokens.expiresIn());

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, tokens.accessToken(), issuedAt, expiresAt);
        OAuth2AuthenticationToken authentication = buildAuthentication(email, storeId, tokens, accessToken);

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        authorizedClientService.saveAuthorizedClient(new OAuth2AuthorizedClient(
                registration,
                authentication.getName(),
                accessToken,
                new OAuth2RefreshToken(tokens.refreshToken(), issuedAt)), authentication);
    }

    private OAuth2AuthenticationToken buildAuthentication(String email, String storeId,
                                                          AuthenticationResultType tokens,
                                                          OAuth2AccessToken accessToken) {
        Map<String, Object> claims = new HashMap<>(parseClaims(tokens.idToken()));
        claims.putIfAbsent(NAME_CLAIM, email);
        claims.putIfAbsent("email", email);

        OidcIdToken idToken = new OidcIdToken(tokens.idToken(),
                accessToken.getIssuedAt(), accessToken.getExpiresAt(), claims);

        Collection<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + UserRole.ADMIN.name()));
        Map<String, String> customAttributes = Map.of(
                "role", UserRole.ADMIN.name(),
                "storeId", storeId);

        CustomUser user = new CustomUser(
                new DefaultOidcUser(authorities, idToken, NAME_CLAIM), accessToken, customAttributes);

        return new OAuth2AuthenticationToken(user, user.getAuthorities(), REGISTRATION_ID);
    }

    private static Map<String, Object> parseClaims(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            return Map.of();
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> claims = new Gson().fromJson(payload, new TypeToken<Map<String, Object>>() {}.getType());
        return claims == null ? Map.of() : claims;
    }

    private static String secretHash(String username, ClientRegistration registration) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(registration.getClientSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal((username + registration.getClientId()).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute Cognito secret hash", e);
        }
    }
}
