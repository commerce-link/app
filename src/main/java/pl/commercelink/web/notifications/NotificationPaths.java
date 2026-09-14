package pl.commercelink.web.notifications;

import pl.commercelink.starter.security.UserRole;

public final class NotificationPaths {

    private NotificationPaths() {
    }

    public static String base(UserRole role, String storeId) {
        return role == UserRole.SUPER_ADMIN
                ? "/dashboard/store/" + storeId + "/notifications"
                : "/dashboard/notifications";
    }
}
