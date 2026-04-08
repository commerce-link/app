package pl.commercelink.starter.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class CustomSecurityContext {

    public static String getStoreId() {
        return getLoggedInUser()
                .flatMap(user -> user.getCustomAttribute("storeId"))
                .orElse(null);
    }

    public static Optional<CustomUser> getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUser) {
            return Optional.of((CustomUser) authentication.getPrincipal());
        }
        return Optional.empty();
    }

    public static String getLoggedInUserName() {
        CustomUser customUser = getLoggedInUser().orElseThrow(() -> new IllegalStateException("No logged in user found"));
        if (StringUtils.isBlank(customUser.getName())) {
            throw new IllegalStateException("Logged in user has no name");
        }
        return customUser.getName();
    }

    private static Set<String> getRoles() {
        return getLoggedInUser()
                .flatMap(user -> user.getCustomAttribute("role"))
                .map(Set::of)
                .orElse(Collections.emptySet());
    }

    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

}
