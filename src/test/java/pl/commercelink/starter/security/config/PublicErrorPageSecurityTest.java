package pl.commercelink.starter.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The error page is public: a customer whose page failed (a missing page under a public path, an exception, a bad
 * parameter) is shown the error page instead of the dashboard login. Pages that are not public still send an
 * anonymous visitor to the login before anything else.
 */
@SpringJUnitWebConfig(PublicErrorPageSecurityTest.Config.class)
@TestPropertySource(properties = "application.env=test")
class PublicErrorPageSecurityTest {

    @MockitoBean private OAuth2AuthorizedClientService authorizedClients;
    @MockitoBean private SecretsManager secretsManager;

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @Controller
    static class StubController {

        @RequestMapping("/error")
        @ResponseBody
        String error() {
            return "error page";
        }

        @RequestMapping("/dashboard/orders")
        @ResponseBody
        String orders() {
            return "orders";
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({WebSecurityConfiguration.class, StubController.class})
    static class Config {

        @Bean
        ClientRegistrationRepository clientRegistrations() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("cognito")
                    .clientId("test").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://login.invalid/oauth2/authorize").tokenUri("https://login.invalid/oauth2/token")
                    .build());
        }

        @Bean
        CustomOAuth2UserService customOAuth2UserService() {
            return new CustomOAuth2UserService();
        }

        @Bean
        CustomAuthenticationSuccessHandler successHandler() {
            return new CustomAuthenticationSuccessHandler("/dashboard");
        }

        @Bean
        CustomLogoutSuccessHandler logoutSuccessHandler(OAuth2AuthorizedClientService clients, SecretsManager secrets,
                                                        Environment environment) {
            return new CustomLogoutSuccessHandler(clients, secrets, environment);
        }

        @Bean
        CustomTokenRefreshFilter tokenRefreshFilter(OAuth2AuthorizedClientService clients, SecretsManager secrets,
                                                    Environment environment) {
            return new CustomTokenRefreshFilter(clients, secrets, environment);
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anAnonymousVisitorIsShownTheErrorPage() throws Exception {
        // when / then
        mvc.perform(get("/error")).andExpect(status().isOk()).andExpect(content().string("error page"));
    }

    @Test
    void theErrorPageAnswersAFailedPostToo() throws Exception {
        // when / then
        mvc.perform(post("/error")).andExpect(status().isOk()).andExpect(content().string("error page"));
    }

    @Test
    void aDashboardPageStillSendsAnAnonymousVisitorToTheLogin() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/orders")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }
}
