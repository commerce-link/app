package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CaptchaVerifierTest {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

    private CaptchaVerifier verifier(String secretKey) {
        return new CaptchaVerifier(restTemplate, "site-key", secretKey);
    }

    @Test
    void acceptsTokenAcceptedByCloudflare() {
        // given
        server.expect(requestTo(SITEVERIFY_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response=token")))
                .andRespond(withSuccess("{\"success\":true,\"challenge_ts\":\"2026-07-08T10:00:00Z\"}",
                        MediaType.APPLICATION_JSON));

        // when / then
        assertTrue(verifier("secret").verify("token", "1.1.1.1"));
        server.verify();
    }

    @Test
    void rejectsTokenRejectedByCloudflare() {
        // given
        server.expect(requestTo(SITEVERIFY_URL))
                .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));

        // when / then
        assertFalse(verifier("secret").verify("token", "1.1.1.1"));
    }

    @Test
    void rejectsWhenCloudflareIsUnavailable() {
        // given
        server.expect(requestTo(SITEVERIFY_URL)).andRespond(withServerError());

        // when / then
        assertFalse(verifier("secret").verify("token", "1.1.1.1"));
    }

    @Test
    void rejectsMissingTokenWithoutCallingCloudflare() {
        // when / then
        assertFalse(verifier("secret").verify(null, "1.1.1.1"));
        assertFalse(verifier("secret").verify("  ", "1.1.1.1"));
        server.verify();
    }

    @Test
    void passesEverythingWhenSecretKeyIsNotConfigured() {
        // given
        CaptchaVerifier verifier = verifier("");

        // when / then
        assertFalse(verifier.isEnabled());
        assertNull(verifier.siteKey());
        assertTrue(verifier.verify(null, "1.1.1.1"));
        server.verify();
    }

    @Test
    void exposesSiteKeyOnlyWhenEnabled() {
        // when / then
        assertEqualsSiteKey("secret", "site-key");
        assertEqualsSiteKey("", null);
    }

    private void assertEqualsSiteKey(String secretKey, String expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, verifier(secretKey).siteKey());
    }
}
