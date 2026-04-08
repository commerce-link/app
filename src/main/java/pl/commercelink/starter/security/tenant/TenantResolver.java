package pl.commercelink.starter.security.tenant;

import jakarta.servlet.http.HttpServletRequest;

public interface TenantResolver {
    String resolveTenantId(HttpServletRequest request);
}
