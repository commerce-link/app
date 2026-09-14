package pl.commercelink.web.nav;

import lombok.extern.slf4j.Slf4j;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.UserRole;

import java.util.Arrays;

@Slf4j
public final class CurrentUserRole {

    private CurrentUserRole() {
    }

    public static UserRole resolve() {
        return CustomSecurityContext.getLoggedInUser()
                .flatMap(user -> user.getCustomAttribute("role"))
                .map(CurrentUserRole::toRole)
                .orElse(null);
    }

    private static UserRole toRole(String role) {
        return Arrays.stream(UserRole.values())
                .filter(known -> known.name().equals(role))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Unknown user role '{}' — rendering the dashboard without role-specific navigation", role);
                    return null;
                });
    }
}
