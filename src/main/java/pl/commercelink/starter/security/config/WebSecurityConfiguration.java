package pl.commercelink.starter.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriter;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService, CustomAuthenticationSuccessHandler successHandler, CustomLogoutSuccessHandler logoutSuccessHandler, CustomTokenRefreshFilter tokenRefreshFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .addHeaderWriter(frameOptions()))
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
