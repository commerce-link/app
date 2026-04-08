package pl.commercelink.starter.security.tenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class PathTenantResolver implements TenantResolver {

    @Override
    public String resolveTenantId(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");

        if (segments.length >= 3 && "Store".equalsIgnoreCase(segments[1])) {
            return segments[2];
        }
        return null;
    }
}
