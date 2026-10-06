package pl.commercelink.starter.security.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** The order page prints the order printouts from a hidden frame; nothing else may be framed. */
class WebSecurityConfigurationTest {

    static String frameOptionsOf(String uri) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        WebSecurityConfiguration.frameOptions().writeHeaders(new MockHttpServletRequest("GET", uri), response);
        return response.getHeader("X-Frame-Options");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard/orders/3e373abc/card", "/dashboard/orders/3e373abc/collection",
            "/dashboard/store/store-1/orders/3e373abc/card", "/dashboard/store/store-1/orders/3e373abc/collection",
            "/dashboard/orders/cards"})
    void theOrderPrintoutsMayBeFramedByTheSameOrigin(String uri) {
        // when
        String header = frameOptionsOf(uri);

        // then
        assertThat(header).isEqualTo("SAMEORIGIN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard/orders/3e373abc", "/dashboard/orders/3e373abc/card/extra", "/login",
            "/dashboard/orders/3e373abc/cancel", "/dashboard/store/store-1/orders/3e373abc", "/dashboard/card",
            "/dashboard/orders/cards/extra", "/dashboard/store/store-1/orders/cards", "/dashboard/orders/list",
            "/dashboard/orders"})
    void everyOtherPageStillRefusesAnyFrame(String uri) {
        // when
        String header = frameOptionsOf(uri);

        // then
        assertThat(header).isEqualTo("DENY");
    }
}
