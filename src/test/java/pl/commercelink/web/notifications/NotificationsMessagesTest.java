package pl.commercelink.web.notifications;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationsMessagesTest {

    private static final List<String> FLASH_KEYS = List.of(
            "notifications.flash.markedRead", "notifications.flash.markedUnread", "notifications.flash.nothingSelected");

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
}
