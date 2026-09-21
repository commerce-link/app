package pl.commercelink.testsupport;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Map;

/** Signs a dashboard user in for controller tests that call handler methods directly. */
public final class SecurityContextLogin {

    private SecurityContextLogin() {
    }

    public static void logInAs(String role, String storeId) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
