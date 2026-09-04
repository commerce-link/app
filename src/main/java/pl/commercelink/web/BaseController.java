package pl.commercelink.web;

import pl.commercelink.starter.security.CustomSecurityContext;

abstract class BaseController {

    String getStoreId () {
        return CustomSecurityContext.getStoreId();
    }

    String getUserId () {
        return CustomSecurityContext.getLoggedInUser()
                .map(user -> user.getAttributes().get("sub"))
                .map(Object::toString)
                .orElse(null);
    }

    boolean isSuperAdmin () {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }

    boolean isAdmin () {
        return CustomSecurityContext.hasRole("ADMIN");
    }
}
