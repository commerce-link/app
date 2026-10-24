package pl.commercelink.web.errorpage;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Errors of the pages sent to a store's customers ({@code /store/{storeId}/client/**}, {@code /store/{storeId}/individual/**}).
 * Only their error dispatch is open to an anonymous visitor: every other error, including those of the shop API and the
 * webhooks, still sends an anonymous caller to the login, as it did before the store's error page existed.
 */
public final class CustomerPageErrors {

    private static final Pattern CUSTOMER_PAGE = Pattern.compile("/store/([^/]+)/(client|individual)(/.*)?");

    /** The error dispatch of a customer page. */
    public static final RequestMatcher DISPATCH = request -> request.getDispatcherType() == DispatcherType.ERROR
            && storeIdOf(originalPath(request)).isPresent();

    private CustomerPageErrors() {
    }

    static String originalPath(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return uri != null ? uri.toString() : request.getRequestURI();
    }

    // matched decoded, as Spring Security and the controllers match the path: /store/s1/%63lient/… is a customer page too
    static Optional<String> storeIdOf(String path) {
        String decoded;
        try {
            decoded = UriUtils.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEscape) {
            return Optional.empty();
        }
        Matcher customerPage = CUSTOMER_PAGE.matcher(decoded);
        return customerPage.matches() ? Optional.of(customerPage.group(1)) : Optional.empty();
    }
}
