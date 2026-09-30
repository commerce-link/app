package pl.commercelink.web.activity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the dashboard of an inactive store read-only: its users still open every page and download every file, but
 * nothing they send is applied. Also leaves the store's trial or deactivation status for the banner.
 */
@Component
public class DashboardReadOnlyInterceptor implements HandlerInterceptor {

    static final String TRIAL_STATUS_ATTRIBUTE = DashboardReadOnlyInterceptor.class.getName() + ".trial";
    static final String DEACTIVATION_STATUS_ATTRIBUTE = DashboardReadOnlyInterceptor.class.getName() + ".deactivation";
    static final String REFUSED_MESSAGE_KEY = "store.inactive.read-only";

    private static final Set<String> READING_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String DASHBOARD_PATH = "/dashboard";

    private final StoresRepository storesRepository;
    private final StoreTrialService storeTrialService;
    private final StoreActivity storeActivity;
    private final MessageSource messageSource;
    private final String homeUrl;

    public DashboardReadOnlyInterceptor(StoresRepository storesRepository, StoreTrialService storeTrialService,
                                        StoreActivity storeActivity, MessageSource messageSource,
                                        @Value("${application.home}") String homeUrl) {
        this.storesRepository = storesRepository;
        this.storeTrialService = storeTrialService;
        this.storeActivity = storeActivity;
        this.messageSource = messageSource;
        this.homeUrl = homeUrl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Optional<Store> store = signedInStore();
        if (store.isEmpty()) {
            return true;
        }
        Optional<DeactivationStatus> deactivation = storeActivity.status(store.get());
        if (deactivation.isEmpty()) {
            storeTrialService.status(store.get())
                    .ifPresent(status -> request.setAttribute(TRIAL_STATUS_ATTRIBUTE, status));
            return true;
        }
        request.setAttribute(DEACTIVATION_STATUS_ATTRIBUTE, deactivation.get());
        if (READING_METHODS.contains(request.getMethod())) {
            return true;
        }
        refuse(request, response);
        return false;
    }

    /** A super admin works on stores named in the path, never on one of their own, so nothing holds them back. */
    private Optional<Store> signedInStore() {
        if (CustomSecurityContext.hasRole(UserRole.SUPER_ADMIN.name())) {
            return Optional.empty();
        }
        String storeId = CustomSecurityContext.getStoreId();
        return storeId == null ? Optional.empty() : Optional.ofNullable(storesRepository.findById(storeId));
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String page = pageSentFrom(request);
        if (SettingsPaths.isAsync(request.getHeader(SettingsPaths.ASYNC_HEADER))) {
            // async-form.js answers a redirect with a regular submit, which comes back here and gets the message; a
            // message saved now as well would wait in the session and show up again on a later visit of the page.
            response.sendRedirect(page);
            return;
        }
        String message = messageSource.getMessage(REFUSED_MESSAGE_KEY, null, RequestContextUtils.getLocale(request));
        if (isPageNavigation(request)) {
            SettingsFlash.forNextPage(request, response, page, "errorMessage", message);
            response.sendRedirect(page);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
        response.getWriter().write(message);
    }

    private static boolean isPageNavigation(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    /** Only a dashboard address is taken from the referer, so the redirect can never lead off the application. */
    private String pageSentFrom(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null) {
            return homeUrl;
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !(path.equals(DASHBOARD_PATH) || path.startsWith(DASHBOARD_PATH + "/"))) {
                return homeUrl;
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return homeUrl;
        }
    }
}
