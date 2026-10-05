package pl.commercelink.starter.security.tenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
public class PathTenantResolver implements TenantResolver {

    @Override
    public String resolveTenantId(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");

        // the request URI is not decoded, but the controllers are matched on the decoded path and get the decoded
        // path variable: read raw, an encoded path (/%53tore/%61bc...) names no store, which the activity check lets by
        if (segments.length >= 3 && "Store".equalsIgnoreCase(decoded(segments[1]))) {
            return decoded(segments[2]);
        }
        return null;
    }

    private static String decoded(String segment) {
        return UriUtils.decode(segment, StandardCharsets.UTF_8);
    }
}
