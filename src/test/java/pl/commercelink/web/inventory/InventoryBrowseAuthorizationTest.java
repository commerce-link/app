package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.ProductsAddReview;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.starter.security.config.WebSecurityConfiguration;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Requests through the application's own security configuration ({@link WebSecurityConfiguration}, with its method
 * security) to the proxied controllers: browsing is everyone's, "Dodaj do katalogu" ("Uzupełnij dane" opened from the
 * inventory, its change of category and its save) is the store admin's -- a user and a super admin get 403 without the
 * handler running. Only the OAuth2 collaborators of that configuration are stand-ins.
 */
@SpringJUnitWebConfig(InventoryBrowseAuthorizationTest.Config.class)
@TestPropertySource(properties = "application.env=test")
class InventoryBrowseAuthorizationTest {

    private static final String STORE_ID = "store-1";

    @MockitoBean private BrowsePageFactory pageFactory;
    @MockitoBean private CatalogTargetOptionsFactory optionsFactory;
    @MockitoBean private CatalogPlacement catalogPlacement;
    @MockitoBean private CatalogAccess access;
    @MockitoBean private ProductsAddReview review;
    @MockitoBean private OAuth2AuthorizedClientService authorizedClients;
    @MockitoBean private SecretsManager secretsManager;

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @Import({WebSecurityConfiguration.class, InventoryBrowseController.class, InventoryAddController.class})
    static class Config {

        /** Views are not rendered here; the prefix only keeps "inventory" from forwarding back to /dashboard/inventory. */
        @Bean
        ViewResolver views() {
            return new InternalResourceViewResolver("/templates/", ".html");
        }

        @Bean
        MessageSource messageSource() {
            StaticMessageSource messages = new StaticMessageSource();
            messages.setUseCodeAsDefaultMessage(true);
            return messages;
        }

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
        when(pageFactory.build(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(BrowsePage.of(BrowsePage.Status.READY, BrowseQuery.start(), false));
        when(optionsFactory.build(any(), anyList())).thenReturn(new CatalogTargetOptions(1, List.of(), List.of(), null, false, null));
    }

    @Test
    void userAndSuperAdminAreRefusedEveryAddRoute() throws Exception {
        // when / then
        for (String role : List.of("USER", "SUPER_ADMIN")) {
            mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001").with(signedInAs(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(forwardedUrl("/access-denied"));
            mvc.perform(post("/dashboard/inventory/add").param("ean", "5901000000001").param("target", "c-1/cat-gpu")
                            .with(signedInAs(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/dashboard/inventory/add/save").param("ean", "5901000000001").param("target", "c-1/cat-gpu")
                            .param("reviewedTarget", "c-1/cat-gpu").with(signedInAs(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/dashboard/inventory/add/save").with(signedInAs(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(optionsFactory, catalogPlacement, access, review);
    }

    @Test
    void storeAdminOpensTheReviewAndItsSaveReloadsBackToTheList() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001")
                        .param("returnTo", "/dashboard/inventory?cat=11").with(signedInAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name(ProductsAddReview.VIEW));
        mvc.perform(post("/dashboard/inventory/add").param("ean", "5901000000001")
                        .param("returnTo", "/dashboard/inventory?cat=11").with(signedInAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name(ProductsAddReview.VIEW));
        mvc.perform(get("/dashboard/inventory/add/save").param("returnTo", "/dashboard/inventory?cat=11")
                        .with(signedInAs("ADMIN")))
                .andExpect(redirectedUrl("/dashboard/inventory?cat=11"));
        verify(optionsFactory, times(2)).build(eq(STORE_ID), eq(List.of("5901000000001")));
    }

    @Test
    void adminAccountWithoutAStoreIsSentBackToTheList() throws Exception {
        // given
        CustomUser user = new CustomUser(new DefaultOAuth2User(List.of(), Map.of("sub", "user-1"), "sub"), null,
                Map.of("role", "ADMIN"));
        RequestPostProcessor storeless = authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // when / then
        mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001").with(storeless))
                .andExpect(redirectedUrl("/dashboard/inventory"));
        verifyNoInteractions(optionsFactory, review);
    }

    @Test
    void everyRoleBrowsesTheBareInventoryPath() throws Exception {
        // when / then
        for (String role : List.of("USER", "ADMIN", "SUPER_ADMIN")) {
            mvc.perform(get("/dashboard/inventory").param("cat", "11").with(signedInAs(role)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("inventory"));
        }
        verify(pageFactory, times(3)).build(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void anonymousVisitorIsSentToTheLoginInsteadOfTheReview() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(optionsFactory);
    }

    /** As the OAuth2 login signs in: the role and store as attributes of the user, the role as its authority. */
    private static RequestPostProcessor signedInAs(String role) {
        CustomUser user = new CustomUser(new DefaultOAuth2User(List.of(), Map.of("sub", "user-1"), "sub"), null,
                "SUPER_ADMIN".equals(role)
                ? Map.of("role", role) : Map.of("storeId", STORE_ID, "role", role));
        return authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
