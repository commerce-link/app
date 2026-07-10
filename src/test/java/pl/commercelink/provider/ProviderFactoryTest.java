package pl.commercelink.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderFactoryTest {

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
}
