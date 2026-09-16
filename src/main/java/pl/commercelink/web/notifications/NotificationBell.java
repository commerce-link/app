package pl.commercelink.web.notifications;

public record NotificationBell(long unreadCount, String dropdownHref, String pageHref) {

    private static final int BADGE_LIMIT = 9;

    public String badgeText() {
        return unreadCount > BADGE_LIMIT ? BADGE_LIMIT + "+" : String.valueOf(unreadCount);
    }
}
