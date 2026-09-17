package pl.commercelink.web.settings;

import pl.commercelink.starter.security.UserRole;

import java.util.Set;

public record SettingsTile(String key, String titleKey, String descriptionKey, String icon, String relativePath,
                           Set<UserRole> roles) {

    public boolean visibleFor(UserRole role) {
        return roles.contains(role);
    }
}
