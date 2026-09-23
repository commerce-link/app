package pl.commercelink.starter.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    @Value("${application.env}")
    private String env;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService, CustomAuthenticationSuccessHandler successHandler, CustomLogoutSuccessHandler logoutSuccessHandler, CustomTokenRefreshFilter tokenRefreshFilter) throws Exception {
        http
                // CSRF protection only for unsafe methods on the session-based admin panel; the /Store,
                // /Global and customer API paths are token/gateway-authenticated and stay exempt. The
                // AndRequestMatcher keeps DEFAULT_CSRF_MATCHER's safe-method exclusion (GET/HEAD/OPTIONS/
                // TRACE), so dashboard reads are never CSRF-gated — only state-changing requests are.
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(new AndRequestMatcher(
                        CsrfFilter.DEFAULT_CSRF_MATCHER,
                        PathPatternRequestMatcher.withDefaults().matcher("/dashboard/**"))))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/",
                            "/env",
                            "/Global/**",
                            "/Store/**",
                            "/store/*/individual/offer/**",
                            "/store/*/client/offer/**",
                            "/store/*/client/rma/**",
                            "/store/*/client/order/**",
                            "/StoreLogo/**",
                            "/css/**",
                            "/js/**",
                            "/register",
                            "/register/password",
                            "/demo/register",
                            "/login",
                            "/logout-success"
                    ).permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(tokenRefreshFilter, OAuth2AuthorizationRequestRedirectFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(successHandler)
                        .userInfoEndpoint(userInfo -> {
                            userInfo.userService(customOAuth2UserService);
                        })
                        .loginPage("/login")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"));

        return http.build();
    }

}
