package pl.commercelink.starter.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

    @Value("${application.env}")
    private String env;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService, CustomAuthenticationSuccessHandler successHandler, CustomLogoutSuccessHandler logoutSuccessHandler, CustomTokenRefreshFilter tokenRefreshFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/",
                            "/env",
                            "/Global/**",
                            "/Store/**",
                            "/store/*/individual/offer/**",
                            "/store/*/client/rma/**",
                            "/StoreLogo/**",
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
