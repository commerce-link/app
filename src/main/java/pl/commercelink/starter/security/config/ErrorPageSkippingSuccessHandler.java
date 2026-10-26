package pl.commercelink.starter.security.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
class ErrorPageSkippingSuccessHandler implements AuthenticationSuccessHandler {

    private static final Set<String> ERROR_PAGES = Set.of("/error", "/access-denied");

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final AuthenticationSuccessHandler delegate;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (isErrorPage(requestCache.getRequest(request, response))) {
            requestCache.removeRequest(request, response);
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }

    private static boolean isErrorPage(SavedRequest saved) {
        return saved instanceof DefaultSavedRequest page
                && ERROR_PAGES.contains(page.getRequestURI().substring(page.getContextPath().length()));
    }
}
