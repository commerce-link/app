package pl.commercelink.web.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsPathsTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void logInAs(String role, String storeId) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void aStoreAdminWorksOnTheSettingsOfTheirOwnStore() {
        // given
        logInAs("ADMIN", "store-1");

        // when / then
        assertThat(SettingsPaths.store("store-1", "/warehouse/addresses/new")).isEqualTo("/dashboard/store/warehouse/addresses/new");
    }

    @Test
    void aSuperAdminWorksOnTheStoreNamedInThePath() {
        // given
        logInAs("SUPER_ADMIN", "none");

        // when / then
        assertThat(SettingsPaths.store("store-9", "/warehouse")).isEqualTo("/dashboard/store/store-9/warehouse");
    }

    @Test
    void onlyTheFetchHeaderMarksAnAsyncSave() {
        // when / then
        assertThat(SettingsPaths.isAsync("fetch")).isTrue();
        assertThat(SettingsPaths.isAsync(null)).isFalse();
        assertThat(SettingsPaths.isAsync("XMLHttpRequest")).isFalse();
    }
}
