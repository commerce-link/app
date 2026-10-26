package pl.commercelink.starter.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.starter.secrets.SecretsManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig(PublicErrorPageSecurityTest.Config.class)
@TestPropertySource(properties = "application.env=test")
class LoginSuccessHandlerWiringTest {

    @MockitoBean private OAuth2AuthorizedClientService authorizedClients;
    @MockitoBean private SecretsManager secretsManager;

    @Autowired private SecurityFilterChain filterChain;

    @Test
    void theOAuth2LoginSkipsErrorPagesBeforeGoingBackToThePageOpenedBeforeLogin() {
        // given
        OAuth2LoginAuthenticationFilter login = filterChain.getFilters().stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst().orElseThrow();

        // when
        Object successHandler = ReflectionTestUtils.getField(login, "successHandler");

        // then
        assertThat(successHandler).isInstanceOf(ErrorPageSkippingSuccessHandler.class);
    }
}
