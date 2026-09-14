package pl.commercelink.web.notifications;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record NotificationView(String id, String titleKey, String message, LocalDateTime createdAt, boolean unread,
                               boolean warning, String actionHref, String actionKey, String openHref) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public String createdAtText() {
        return createdAt == null ? "" : createdAt.format(DATE_FORMAT);
    }

    public String cssClass() {
        return warning ? "is-warn" : "is-info";
    }

    public String icon() {
        return warning ? "fa-exclamation-triangle" : "fa-info-circle";
    }
}
