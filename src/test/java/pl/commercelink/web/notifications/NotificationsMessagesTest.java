package pl.commercelink.web.notifications;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationsMessagesTest {

    private static final List<String> FLASH_KEYS = List.of(
            "notifications.flash.markedRead", "notifications.flash.markedUnread", "notifications.flash.nothingSelected");

    private static final List<String> PAGE_KEYS = List.of(
            "notifications.title", "notifications.markAllRead", "notifications.markAllRead.short", "notifications.viewAll",
            "notifications.empty.all", "notifications.empty.unread", "notifications.filter.aria",
            "notifications.filter.unread", "notifications.filter.all", "notifications.filter.type",
            "notifications.filter.allTypes", "notifications.selectAll",
            "notifications.select.item", "notifications.markRead", "notifications.markUnread",
            "notifications.unreadBadge", "notifications.unread.sr", "notifications.pagination.label");

    private static final List<String> BELL_KEYS = List.of(
            "notifications.bell.aria", "notifications.loading", "notifications.loadError");

    private static void assertTranslated(List<String> keys) {
        for (String language : List.of("pl", "en")) {
            // given
            ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));

            // when / then
            keys.forEach(key -> assertThat(messages.containsKey(key)).as(language + " " + key).isTrue());
        }
    }

    @Test
    void translatesTheBulkActionMessagesInBothLanguages() {
        // when / then
        assertTranslated(FLASH_KEYS);
    }

    @Test
    void translatesThePageAndDropdownTextsInBothLanguages() {
        // when / then
        assertTranslated(PAGE_KEYS);
    }

    @Test
    void translatesTheBellTextsInBothLanguages() {
        // when / then
        assertTranslated(BELL_KEYS);
    }
}
