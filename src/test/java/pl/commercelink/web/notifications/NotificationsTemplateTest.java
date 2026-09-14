package pl.commercelink.web.notifications;

import org.junit.jupiter.api.Test;
import pl.commercelink.notifications.NotificationPage;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationsTemplateTest {

    private static final String DROPDOWN = "<div th:replace=\"~{fragments/notifications :: dropdown}\"></div>";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 14, 15, 32);

    private static NotificationView unreadExpiredConnection() {
        return new NotificationView("UNAUTHENTICATED:allegro_marketplace", "store.notification.type.UNAUTHENTICATED",
                "Your connection to Allegro marketplace has expired", CREATED_AT, true, true,
                "/dashboard/store/marketplaces", "store.notification.action.reconnect",
                "/dashboard/notifications/UNAUTHENTICATED:allegro_marketplace/open");
    }

    private static NotificationView readUnmatchedReturn() {
        return new NotificationView("MARKETPLACE_RETURN_UNMATCHED:ret-1", "store.notification.type.MARKETPLACE_RETURN_UNMATCHED",
                "Allegro return AL-5521 could not be matched", CREATED_AT.minusDays(1), false, true, null, null,
                "/dashboard/notifications/MARKETPLACE_RETURN_UNMATCHED:ret-1/open");
    }

    private static Map<String, Object> dropdown(List<NotificationView> notifications, long unreadCount) {
        return Map.of("basePath", "/dashboard/notifications", "notifications", notifications, "unreadCount", unreadCount);
    }

    private static Map<String, Object> page(List<NotificationView> notifications, boolean unreadOnly, int currentPage,
                                            int totalPages, long unreadCount) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("basePath", "/dashboard/notifications");
        variables.put("notifications", notifications);
        variables.put("notificationPage", new NotificationPage(List.of(), currentPage, totalPages, notifications.size(), unreadCount));
        variables.put("unreadOnly", unreadOnly);
        variables.put("selectedType", null);
        variables.put("types", StoreNotificationType.values());
        variables.put("unreadTabHref", "/dashboard/notifications?filter=unread");
        variables.put("allTabHref", "/dashboard/notifications?filter=all");
        variables.put("pageHref", unreadOnly ? "/dashboard/notifications?filter=unread" : "/dashboard/notifications?filter=all");
        variables.put("currentPage", currentPage);
        variables.put("hasNextPage", currentPage < totalPages);
        variables.put("paginationParams", Map.of("filter", unreadOnly ? "unread" : "all"));
        return variables;
    }

    @Test
    void marksTheDropdownRootSoTheBellCanTellItFromALoginPage() {
        // when
        String html = SettingsTemplateRenderer.render(DROPDOWN, dropdown(List.of(unreadExpiredConnection()), 1));

        // then
        assertThat(html).contains("data-notifications-dropdown");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void listsEachNotificationInTheDropdownAsARowThatOpensIt() {
        // when
        String html = SettingsTemplateRenderer.render(DROPDOWN,
                dropdown(List.of(unreadExpiredConnection(), readUnmatchedReturn()), 1));

        // then
        assertThat(html.split("class=\"cl-notification-row", -1)).hasSize(3);
        assertThat(html.split("class=\"cl-notification-row is-unread\"", -1)).hasSize(2);
        assertThat(html).contains("Wygasło połączenie z marketplace").contains("Your connection to Allegro marketplace has expired");
        assertThat(html).contains("14.09.2026 15:32");
        assertThat(html).contains("action=\"/dashboard/notifications/UNAUTHENTICATED:allegro_marketplace/open\"");
        assertThat(html).contains("action=\"/dashboard/notifications/read-all\"").contains("Oznacz wszystkie");
        assertThat(html).contains("name=\"redirect\" value=\"/dashboard/notifications\"");
        assertThat(html).contains("href=\"/dashboard/notifications?filter=all\"").contains("Zobacz wszystkie");
        assertThat(html.split(">nieprzeczytane<", -1)).hasSize(2);
    }

    @Test
    void offersNoMarkAllWithoutUnreadNotificationsAndSaysWhenThereAreNone() {
        // when
        String html = SettingsTemplateRenderer.render(DROPDOWN, dropdown(List.of(), 0));

        // then
        assertThat(html).contains("Brak powiadomień");
        assertThat(html).doesNotContain("read-all").doesNotContain("cl-notification-row");
    }

    @Test
    void showsTheUnreadBadgeTheStateTabsAndTheTypeFilterOnThePage() {
        // when
        String html = SettingsTemplateRenderer.render("notifications",
                page(List.of(unreadExpiredConnection(), readUnmatchedReturn()), false, 1, 1, 1));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Powiadomienia</h1>");
        assertThat(html).contains("Nieprzeczytane: 1");
        assertThat(html).contains("action=\"/dashboard/notifications/read-all\"").contains("Oznacz wszystkie jako przeczytane");
        assertThat(html).contains("href=\"/dashboard/notifications?filter=unread\"").contains("href=\"/dashboard/notifications?filter=all\"");
        assertThat(html.split("aria-current=\"page\"", -1)).hasSize(2);
        assertThat(html).contains("value=\"MARKETPLACE_RETURN_UNMATCHED\"").contains("Nieprzypisany zwrot z marketplace");
        assertThat(html).contains("Wszystkie typy").contains("data-filter-auto-submit").doesNotContain("Filtruj");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void letsTheOperatorActOnEachNotificationAndOnTheSelectedOnes() {
        // when
        String html = SettingsTemplateRenderer.render("notifications",
                page(List.of(unreadExpiredConnection(), readUnmatchedReturn()), false, 1, 1, 1));

        // then
        assertThat(html).contains("id=\"notificationsBulk\"").contains("data-select-all");
        assertThat(html.split("form=\"notificationsBulk\"", -1)).hasSize(3);
        assertThat(html).contains("formaction=\"/dashboard/notifications/read\"").contains("formaction=\"/dashboard/notifications/unread\"");
        assertThat(html).contains("Połącz ponownie")
                .contains("action=\"/dashboard/notifications/UNAUTHENTICATED:allegro_marketplace/open\"");
        assertThat(html.split("Oznacz jako przeczytane<", -1)).hasSize(3);
        assertThat(html.split("Oznacz jako nieprzeczytane<", -1)).hasSize(3);
        assertThat(html).contains("value=\"/dashboard/notifications?filter=all\"");
        assertThat(html).contains("class=\"cl-notification-item is-unread\"");
    }

    @Test
    void wrapsEachRowCheckboxInAFortyFourPixelTouchTargetLabel() {
        // given
        List<NotificationView> notifications = List.of(unreadExpiredConnection(), readUnmatchedReturn());

        // when
        String html = SettingsTemplateRenderer.render("notifications", page(notifications, false, 1, 1, 1));
        String normalized = html.replaceAll("\\s+", " ");

        // then
        assertThat(html.split("class=\"cl-notification-select-hit\"", -1)).hasSize(3);
        assertThat(normalized)
                .contains("<label class=\"cl-notification-select-hit\"> <input type=\"checkbox\" name=\"ids\" "
                        + "form=\"notificationsBulk\" class=\"cl-notification-select\"")
                .contains("</label> <span class=\"cl-notification-dot\"");
    }

    @Test
    void showsTheEmptyStateOfTheSelectedFilter() {
        // when
        String unread = SettingsTemplateRenderer.render("notifications", page(List.of(), true, 1, 1, 0));
        String all = SettingsTemplateRenderer.render("notifications", page(List.of(), false, 1, 1, 0));

        // then
        assertThat(unread).contains("Brak nieprzeczytanych powiadomień");
        assertThat(unread).doesNotContain("notificationsBulk").doesNotContain("read-all");
        assertThat(all).contains("Brak powiadomień");
    }

    @Test
    void paginatesOnlyWhenThereIsMoreThanOnePage() {
        // when
        String twoPages = SettingsTemplateRenderer.render("notifications",
                page(List.of(unreadExpiredConnection()), false, 1, 2, 1));
        String onePage = SettingsTemplateRenderer.render("notifications",
                page(List.of(unreadExpiredConnection()), false, 1, 1, 1));

        // then
        assertThat(twoPages).contains("Strona 1 z 2").contains("pagination-next");
        assertThat(onePage).doesNotContain("pagination-next").doesNotContain("Strona 1 z");
    }
}
