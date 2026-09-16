package pl.commercelink.web.notifications;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.nav.CurrentUserRole;
import pl.commercelink.web.nav.StorePath;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class NotificationBellAdvice {

    private final StoreNotificationService notificationService;

    @ModelAttribute("notificationBell")
    public NotificationBell notificationBell(HttpServletRequest request) {
        if (!rendersAPage(request)) {
            return null;
        }
        UserRole role = CurrentUserRole.resolve();
        String storeId = storeIdFor(role, request);
        if (storeId == null) {
            return null;
        }
        try {
            String basePath = NotificationPaths.base(role, storeId);
            return new NotificationBell(notificationService.unreadCount(storeId), basePath + "/dropdown", basePath);
        } catch (RuntimeException e) {
            log.warn("Could not count unread notifications of store {}, rendering the page without the bell", storeId, e);
            return null;
        }
    }

    private static String storeIdFor(UserRole role, HttpServletRequest request) {
        if (role == UserRole.ADMIN) {
            return CustomSecurityContext.getStoreId();
        }
        if (role == UserRole.SUPER_ADMIN) {
            return StorePath.storeIdIn(request.getRequestURI());
        }
        return null;
    }

    // model attributes are computed for every handler, JSON and fetch calls included, but only a page view (a
    // browser navigation, never a form POST) shows the bell
    private static boolean rendersAPage(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("text/html");
    }
}
