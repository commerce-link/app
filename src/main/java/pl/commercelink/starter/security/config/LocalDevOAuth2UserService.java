package pl.commercelink.starter.security.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workaround for Ministack not returning custom attributes in the userinfo response.
 * Falls back to the Cognito GetUser API to fetch custom:role, custom:storeId, etc.
 * Active only with the "localdev" Spring profile.
 */
@Service
@Profile("localdev")
@Primary
public class LocalDevOAuth2UserService extends CustomOAuth2UserService {

    @Value("${cognito.domain}")
    private String cognitoDomain;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);

        if (!(user instanceof CustomUser customUser)) return user;
        if (!customUser.customAttributes().isEmpty()) return user;

        Map<String, String> customAttributes = fetchCustomAttributes(userRequest.getAccessToken().getTokenValue());
        if (customAttributes.isEmpty()) return user;

        return new CustomUser(customUser.oAuth2User(), userRequest.getAccessToken(), customAttributes);
    }

    private Map<String, String> fetchCustomAttributes(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cognitoDomain))
                    .header("Content-Type", "application/x-amz-json-1.1")
                    .header("X-Amz-Target", "AWSCognitoIdentityProviderService.GetUser")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"AccessToken\":\"" + accessToken + "\"}"))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> body = new Gson().fromJson(
                    response.body(), new TypeToken<Map<String, Object>>() {}.getType()
            );

            Map<String, String> result = new HashMap<>();
            @SuppressWarnings("unchecked")
            List<Map<String, String>> attrs = (List<Map<String, String>>) body.get("UserAttributes");
            if (attrs != null) {
                for (Map<String, String> attr : attrs) {
                    String name = attr.get("Name");
                    String value = attr.get("Value");
                    if (name != null && name.startsWith("custom:") && value != null) {
                        result.put(name.substring("custom:".length()), value);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("LocalDev: failed to fetch custom attributes from Cognito: " + e.getMessage());
            return Map.of();
        }
    }

}
