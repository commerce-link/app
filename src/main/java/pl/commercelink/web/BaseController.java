package pl.commercelink.web;

import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;

abstract class BaseController {

    String getStoreId () {
        return CustomSecurityContext.getStoreId();
    }

    String getUserId () {
        return CustomSecurityContext.getLoggedInUser().map(CustomUser::getName).orElse(null);
    }

    boolean isSuperAdmin () {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }

    boolean isAdmin () {
        return CustomSecurityContext.hasRole("ADMIN");
    }
}
