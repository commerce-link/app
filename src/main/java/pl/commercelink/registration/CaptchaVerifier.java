package pl.commercelink.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@ConditionalOnProperty(name = "app.registration.enabled", havingValue = "true")
public class CaptchaVerifier {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestTemplate restTemplate;
    private final String siteKey;
    private final String secretKey;

    @Autowired
    public CaptchaVerifier(@Value("${app.registration.captcha.site-key:}") String siteKey,
                           @Value("${app.registration.captcha.secret-key:}") String secretKey) {
        this(new RestTemplate(), siteKey, secretKey);
    }

    CaptchaVerifier(RestTemplate restTemplate, String siteKey, String secretKey) {
        this.restTemplate = restTemplate;
        this.siteKey = siteKey;
        this.secretKey = secretKey;
    }

    public String siteKey() {
        return isEnabled() && isNotBlank(siteKey) ? siteKey : null;
    }

    public boolean isEnabled() {
        return isNotBlank(secretKey);
    }

    public boolean verify(String token, String clientIp) {
        if (!isEnabled()) {
            return true;
        }
        if (isBlank(token)) {
            return false;
        }
        try {
            SiteVerifyResponse response = restTemplate.postForObject(
                    SITEVERIFY_URL, new HttpEntity<>(form(token, clientIp), formHeaders()), SiteVerifyResponse.class);
            return response != null && response.success();
        } catch (RestClientException e) {
            System.err.println("[Registration] Captcha verification call failed: " + e.getMessage());
            return false;
        }
    }

    private MultiValueMap<String, String> form(String token, String clientIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (isNotBlank(clientIp)) {
            form.add("remoteip", clientIp);
        }
        return form;
    }

    private static HttpHeaders formHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteVerifyResponse(boolean success) {
    }
}
