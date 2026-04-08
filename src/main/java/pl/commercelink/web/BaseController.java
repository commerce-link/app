package pl.commercelink.web;

import pl.commercelink.starter.security.CustomSecurityContext;

abstract class BaseController {

    String getStoreId () {
        return CustomSecurityContext.getStoreId();
    }

    boolean isSuperAdmin () {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }

    boolean isAdmin () {
        return CustomSecurityContext.hasRole("ADMIN");
    }
}
