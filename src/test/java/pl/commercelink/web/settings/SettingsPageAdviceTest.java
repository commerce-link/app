package pl.commercelink.web.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsPageAdviceTest {

    private final SettingsPageAdvice advice = new SettingsPageAdvice();

    private void loggedInAs(String role) {
        CustomUser user = new CustomUser(
                new DefaultOAuth2User(List.of(), Map.of("sub", "user-1"), "sub"), null, Map.of("role", role));
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void describesTheSettingsPageForTheLoggedInRole() {
        // given
        loggedInAs("SUPER_ADMIN");

        // when
        SettingsPage page = advice.settingsPage(new MockHttpServletRequest("GET", "/dashboard/store/store-1/branding"));

        // then
        assertThat(page.tile().key()).isEqualTo("branding");
        assertThat(page.homeHref()).isEqualTo("/dashboard/store/store-1");
    }

    @Test
    void describesNothingWhenNobodyIsLoggedIn() {
        // when / then
        assertThat(advice.settingsPage(new MockHttpServletRequest("GET", "/dashboard/store/branding"))).isNull();
    }
}
