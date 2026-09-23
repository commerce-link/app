package pl.commercelink.starter.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link WebSecurityConfiguration} filter chain to pin the CSRF matcher behaviour.
 * The earlier regression (a path-only matcher that also gated safe methods, so every GET /dashboard/**
 * returned 403) slipped through because no test ran the security chain — {@link #getOnDashboardIsNotCsrfGated()}
 * is the case that now catches it. The context is pinned to {@link TestContext} so the app's real
 * controllers/AWS beans are not component-scanned.
 */
@WebMvcTest(excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@ContextConfiguration(classes = DashboardCsrfSecurityTest.TestContext.class)
@TestPropertySource(properties = "application.env=localhost")
class DashboardCsrfSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler logoutSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void getOnDashboardIsNotCsrfGated() throws Exception {
        // given a logged-in admin issuing a safe (GET) request without a CSRF token
        // when / then the read must pass — CSRF only guards state-changing methods
        mvc.perform(get("/dashboard/probe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void postOnDashboardWithoutTokenIsForbidden() throws Exception {
        // given a logged-in admin posting without a CSRF token
        // when / then it is denied with 403 (not 405), via the access-denied handler
        mvc.perform(post("/dashboard/probe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postOnDashboardWithTokenIsAccepted() throws Exception {
        // given a logged-in admin posting with a valid CSRF token
        // when / then the request reaches the handler
        mvc.perform(post("/dashboard/probe").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void postOnDashboardWithInvalidTokenIsForbidden() throws Exception {
        // given a logged-in admin posting with a bad CSRF token
        // when / then it is denied
        mvc.perform(post("/dashboard/probe").with(user("admin").roles("ADMIN")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void postOnStoreApiIsNotCsrfGated() throws Exception {
        // given a call to the gateway-authenticated /Store API with no CSRF token
        // when / then it stays exempt — the matcher covers /dashboard/** only
        mvc.perform(post("/Store/probe"))
                .andExpect(status().isOk());
    }

    @Configuration
    @Import(WebSecurityConfiguration.class)
    static class TestContext {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        // A real CustomTokenRefreshFilter cannot be mocked (a mock would swallow the chain); this
        // instance skips straight to the next filter so the chain reaches the security matchers.
        @Bean
        CustomTokenRefreshFilter tokenRefreshFilter() {
            return new CustomTokenRefreshFilter(mock(OAuth2AuthorizedClientService.class),
                    mock(SecretsManager.class), mock(Environment.class)) {
                @Override
                public void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/dashboard/probe")
        String getDashboard() {
            return "ok";
        }

        @PostMapping("/dashboard/probe")
        String postDashboard() {
            return "ok";
        }

        @PostMapping("/Store/probe")
        String postStore() {
            return "ok";
        }
    }
}
