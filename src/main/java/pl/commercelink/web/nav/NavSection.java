package pl.commercelink.web.nav;

import pl.commercelink.starter.security.UserRole;

import java.util.List;

public record NavSection(String messageKey, List<NavItem> items) {

    public NavSection filteredFor(UserRole role) {
        return new NavSection(messageKey, items.stream().filter(item -> item.visibleFor(role)).toList());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
