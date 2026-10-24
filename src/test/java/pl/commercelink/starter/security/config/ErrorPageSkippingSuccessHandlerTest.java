package pl.commercelink.starter.security.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorPageSkippingSuccessHandlerTest {

    private final ErrorPageSkippingSuccessHandler handler =
            new ErrorPageSkippingSuccessHandler(new CustomAuthenticationSuccessHandler("/dashboard"));

    @ParameterizedTest
    @ValueSource(strings = {"/error", "/access-denied"})
    void anErrorPageOpenedBeforeLoginIsNotWhereTheLoginLeads(String errorPage) throws Exception {
        // given
        MockHttpServletRequest login = loginAfterOpening(errorPage);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(login, response, new TestingAuthenticationToken("admin", null));

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @ParameterizedTest
    @CsvSource({
            "/dashboard/orders/o-1, http://localhost/dashboard/orders/o-1?continue",
            "/dashboard/store/discounts, http://localhost/dashboard/store/discounts?continue"
    })
    void aPageOpenedBeforeLoginIsStillWhereTheLoginLeads(String page, String target) throws Exception {
        // given
        MockHttpServletRequest login = loginAfterOpening(page);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(login, response, new TestingAuthenticationToken("admin", null));

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo(target);
    }

    private static MockHttpServletRequest loginAfterOpening(String path) {
        MockHttpServletRequest opened = new MockHttpServletRequest("GET", path);
        opened.addHeader("Accept", "text/html,application/xhtml+xml");
        new HttpSessionRequestCache().saveRequest(opened, new MockHttpServletResponse());
        MockHttpServletRequest login = new MockHttpServletRequest("GET", "/login/oauth2/code/cognito");
        login.setSession(opened.getSession());
        return login;
    }
}
