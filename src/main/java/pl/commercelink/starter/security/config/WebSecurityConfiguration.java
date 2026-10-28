package pl.commercelink.starter.security.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;
import pl.commercelink.web.errorpage.CustomerPageErrors;

import java.util.function.Supplier;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    // The order printouts (card, collection protocol, and the batch of cards printed from the orders list) are printed
    // from a hidden frame of the page that asks for them, so they may be framed by a page of this origin; every other
    // page keeps Spring Security's default DENY against clickjacking.
    static final Pattern FRAMEABLE_BY_SAME_ORIGIN =
            Pattern.compile("/dashboard/(store/[^/]+/)?orders/[^/]+/(card|collection)|/dashboard/orders/cards");

    @Value("${application.env}")
    private String env;

    static HeaderWriter frameOptions() {
        return (request, response) -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            response.setHeader("X-Frame-Options",
                    FRAMEABLE_BY_SAME_ORIGIN.matcher(path).matches() ? "SAMEORIGIN" : "DENY");
        };
    }

    private static RequestMatcher dashboard() {
        return PathPatternRequestMatcher.withDefaults().matcher("/dashboard/**");
    }

    private static final class DashboardCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();
        private final RequestMatcher dashboard;

        private DashboardCsrfTokenRequestHandler(RequestMatcher dashboard) {
            this.dashboard = dashboard;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            if (dashboard.matches(request)) {
                delegate.handle(request, response, csrfToken);
            }
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService, CustomAuthenticationSuccessHandler successHandler, CustomLogoutSuccessHandler logoutSuccessHandler, CustomTokenRefreshFilter tokenRefreshFilter) throws Exception {
        http
                // CSRF protection only for unsafe methods on the session-based admin panel; the /Store,
                // /Global and customer API paths are token/gateway-authenticated and stay exempt. The
                // AndRequestMatcher keeps DEFAULT_CSRF_MATCHER's safe-method exclusion (GET/HEAD/OPTIONS/
                // TRACE), so dashboard reads are never CSRF-gated — only state-changing requests are.
                // The token is exposed to templates on the dashboard only: storing it creates a session, which a
                // customer page cannot do once its response is partly sent (a long offer is cut off at the form).
                .csrf(csrf -> csrf
                        .requireCsrfProtectionMatcher(new AndRequestMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER, dashboard()))
                        .csrfTokenRequestHandler(new DashboardCsrfTokenRequestHandler(dashboard())))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .addHeaderWriter(frameOptions()))
                .authorizeHttpRequests(auth -> {
                    // Only the error of a customer page is open, so its customer sees the store's error page
                    // (PublicErrorViewResolver) instead of the dashboard login. Any other error, including those of the
                    // shop API and the webhooks, still sends an anonymous caller to the login, as it always has.
                    auth.requestMatchers(CustomerPageErrors.DISPATCH).permitAll();
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
                        .successHandler(new ErrorPageSkippingSuccessHandler(successHandler))
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
