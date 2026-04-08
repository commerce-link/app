package pl.commercelink.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StoreAccessInterceptor implements HandlerInterceptor {

    private static final Pattern STORE_PATH_PATTERN = Pattern.compile("/dashboard/store/([^/]+)(/.*)?");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        Matcher matcher = STORE_PATH_PATTERN.matcher(path);

        if (matcher.matches()) {
            String storeId = matcher.group(1);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                response.sendRedirect("/access-denied");
                return false;
            }

            CustomUser user = (CustomUser) auth.getPrincipal();
            if (user == null) {
                response.sendRedirect("/access-denied");
                return false;
            }

            UserRole role = UserRole.valueOf(user.getCustomAttribute("role").orElseThrow());
            if (role == UserRole.SUPER_ADMIN) return true;

            // User is not belong to the store
            if (!Objects.equals(user.getAttribute("storeId"), storeId)) {
                response.sendRedirect("/access-denied");
                return false;
            }
        }

        return true;
    }

}