package pl.commercelink.web.nav;

import pl.commercelink.starter.security.UserRole;

import java.util.Set;

public record NavItem(String key, String messageKey, String path, String icon, Set<UserRole> roles) {

    public boolean visibleFor(UserRole role) {
        return roles.contains(role);
    }
}
