package pl.commercelink.web.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.notifications.NotificationFilter;
import pl.commercelink.notifications.NotificationPage;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.StoreNotificationType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class NotificationsController {

    static final int DROPDOWN_LIMIT = 10;
    static final int PAGE_SIZE = 50;

    private final StoreNotificationService notificationService;
    private final NotificationViewFactory viewFactory;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public String notifications(@RequestParam(defaultValue = "unread") String filter,
                                @RequestParam(required = false) String type,
                                @RequestParam(defaultValue = "1") int page,
                                Model model) {
        return renderPage(UserRole.ADMIN, CustomSecurityContext.getStoreId(), filter, type, page, model);
    }

    @GetMapping("/dashboard/store/{storeId}/notifications")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String storeNotifications(@PathVariable String storeId,
                                     @RequestParam(defaultValue = "unread") String filter,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(defaultValue = "1") int page,
                                     Model model) {
        return renderPage(UserRole.SUPER_ADMIN, storeId, filter, type, page, model);
    }

    @GetMapping("/dashboard/notifications/dropdown")
    @PreAuthorize("hasRole('ADMIN')")
    public String dropdown(Model model) {
        return renderDropdown(UserRole.ADMIN, CustomSecurityContext.getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/notifications/dropdown")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String storeDropdown(@PathVariable String storeId, Model model) {
        return renderDropdown(UserRole.SUPER_ADMIN, storeId, model);
    }

    @PostMapping("/dashboard/notifications/{notificationId}/open")
    @PreAuthorize("hasRole('ADMIN')")
    public String open(@PathVariable String notificationId) {
        return openNotification(UserRole.ADMIN, CustomSecurityContext.getStoreId(), notificationId);
    }

    @PostMapping("/dashboard/store/{storeId}/notifications/{notificationId}/open")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String storeOpen(@PathVariable String storeId, @PathVariable String notificationId) {
        return openNotification(UserRole.SUPER_ADMIN, storeId, notificationId);
    }

    @PostMapping("/dashboard/notifications/read")
    @PreAuthorize("hasRole('ADMIN')")
    public String markRead(@RequestParam(name = "ids", required = false) List<String> ids,
                           @RequestParam(required = false) String redirect,
                           Locale locale, RedirectAttributes redirectAttributes) {
        return mark(UserRole.ADMIN, CustomSecurityContext.getStoreId(), ids, true, redirect, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/notifications/read")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String storeMarkRead(@PathVariable String storeId,
                                @RequestParam(name = "ids", required = false) List<String> ids,
                                @RequestParam(required = false) String redirect,
                                Locale locale, RedirectAttributes redirectAttributes) {
        return mark(UserRole.SUPER_ADMIN, storeId, ids, true, redirect, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/notifications/unread")
    @PreAuthorize("hasRole('ADMIN')")
    public String markUnread(@RequestParam(name = "ids", required = false) List<String> ids,
                             @RequestParam(required = false) String redirect,
                             Locale locale, RedirectAttributes redirectAttributes) {
        return mark(UserRole.ADMIN, CustomSecurityContext.getStoreId(), ids, false, redirect, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/notifications/unread")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String storeMarkUnread(@PathVariable String storeId,
                                  @RequestParam(name = "ids", required = false) List<String> ids,
                                  @RequestParam(required = false) String redirect,
                                  Locale locale, RedirectAttributes redirectAttributes) {
        return mark(UserRole.SUPER_ADMIN, storeId, ids, false, redirect, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/notifications/read-all")
    @PreAuthorize("hasRole('ADMIN')")
    public String markAllRead(@RequestParam(required = false) String redirect,
                              Locale locale, RedirectAttributes redirectAttributes) {
        return markAll(UserRole.ADMIN, CustomSecurityContext.getStoreId(), redirect, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/notifications/read-all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String storeMarkAllRead(@PathVariable String storeId,
                                   @RequestParam(required = false) String redirect,
                                   Locale locale, RedirectAttributes redirectAttributes) {
        return markAll(UserRole.SUPER_ADMIN, storeId, redirect, locale, redirectAttributes);
    }

    static String safeRedirect(String redirect, String basePath) {
        // RedirectView expands {…} as a URI template variable, which throws when the value is not a known one
        boolean withinDashboard = redirect != null && redirect.startsWith("/dashboard")
                && !redirect.contains("//") && !redirect.contains("\\")
                && !redirect.contains("{") && !redirect.contains("}");
        return withinDashboard ? redirect : basePath;
    }

    private String renderPage(UserRole role, String storeId, String filter, String type, int page, Model model) {
        String basePath = NotificationPaths.base(role, storeId);
        boolean unreadOnly = !"all".equals(filter);
        StoreNotificationType selectedType = parseType(type);
        NotificationPage result = notificationService.list(storeId, new NotificationFilter(unreadOnly, selectedType),
                page, PAGE_SIZE);

        model.addAttribute("basePath", basePath);
        model.addAttribute("notificationPage", result);
        model.addAttribute("notifications", viewFactory.toViews(result.items(), role));
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("selectedType", selectedType == null ? null : selectedType.name());
        model.addAttribute("types", StoreNotificationType.values());
        model.addAttribute("unreadTabHref", listHref(basePath, true, selectedType, 1));
        model.addAttribute("allTabHref", listHref(basePath, false, selectedType, 1));
        model.addAttribute("pageHref", listHref(basePath, unreadOnly, selectedType, result.page()));
        model.addAttribute("currentPage", result.page());
        model.addAttribute("hasNextPage", result.page() < result.totalPages());
        model.addAttribute("paginationParams", paginationParams(unreadOnly, selectedType));
        return "notifications";
    }

    private String renderDropdown(UserRole role, String storeId, Model model) {
        model.addAttribute("basePath", NotificationPaths.base(role, storeId));
        model.addAttribute("notifications", viewFactory.toViews(notificationService.latest(storeId, DROPDOWN_LIMIT), role));
        model.addAttribute("unreadCount", notificationService.unreadCount(storeId));
        return "fragments/notifications :: dropdown";
    }

    private String openNotification(UserRole role, String storeId, String notificationId) {
        String allNotifications = NotificationPaths.base(role, storeId) + "?filter=all";
        return notificationService.find(storeId, notificationId)
                .map(record -> {
                    notificationService.markRead(storeId, List.of(notificationId));
                    String actionHref = viewFactory.toView(record, role).actionHref();
                    return "redirect:" + (actionHref != null ? actionHref : allNotifications);
                })
                .orElse("redirect:" + allNotifications);
    }

    private String mark(UserRole role, String storeId, List<String> ids, boolean read, String redirect, Locale locale,
                        RedirectAttributes redirectAttributes) {
        String basePath = NotificationPaths.base(role, storeId);
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    messageSource.getMessage("notifications.flash.nothingSelected", null, locale));
            return "redirect:" + safeRedirect(redirect, basePath);
        }
        int marked = read ? notificationService.markRead(storeId, ids) : notificationService.markUnread(storeId, ids);
        String key = read ? "notifications.flash.markedRead" : "notifications.flash.markedUnread";
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage(key, new Object[]{marked}, locale));
        return "redirect:" + safeRedirect(redirect, basePath);
    }

    private String markAll(UserRole role, String storeId, String redirect, Locale locale,
                           RedirectAttributes redirectAttributes) {
        int marked = notificationService.markAllRead(storeId);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("notifications.flash.markedRead", new Object[]{marked}, locale));
        return "redirect:" + safeRedirect(redirect, NotificationPaths.base(role, storeId));
    }

    private static StoreNotificationType parseType(String type) {
        return Arrays.stream(StoreNotificationType.values())
                .filter(candidate -> candidate.name().equals(type))
                .findFirst()
                .orElse(null);
    }

    private static String listHref(String basePath, boolean unreadOnly, StoreNotificationType type, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath).queryParam("filter", filterValue(unreadOnly));
        if (type != null) {
            builder.queryParam("type", type.name());
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        return builder.build().toUriString();
    }

    private static Map<String, String> paginationParams(boolean unreadOnly, StoreNotificationType type) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("filter", filterValue(unreadOnly));
        if (type != null) {
            params.put("type", type.name());
        }
        return params;
    }

    private static String filterValue(boolean unreadOnly) {
        return unreadOnly ? "unread" : "all";
    }
}
