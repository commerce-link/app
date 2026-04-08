package pl.commercelink.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.commercelink.starter.security.tenant.ApiKeyValidator;
import pl.commercelink.starter.security.tenant.TenantResolver;

@Component
public class StoreApiKeyAuthorizationInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "x-api-key";

    private final ApiKeyValidator apiKeyValidator;
    private final TenantResolver tenantResolver;

    public StoreApiKeyAuthorizationInterceptor(ApiKeyValidator apiKeyValidator, TenantResolver tenantResolver) {
        this.apiKeyValidator = apiKeyValidator;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = request.getHeader(API_KEY_HEADER);
        String tenantId = tenantResolver.resolveTenantId(request);

        if (!apiKeyValidator.isValid(apiKey, tenantId)) {
            respond(response, HttpStatus.FORBIDDEN);
            return false;
        }

        return true;
    }

    private void respond(HttpServletResponse response, HttpStatus status) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Forbidden\"}");
    }
}
