package pl.commercelink.web.nav;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationMessagesTest {

    private static final List<String> CONTROL_KEYS = List.of(
            "nav.aria.main", "nav.aria.breadcrumb", "nav.skip.to.content", "nav.menu.open", "nav.menu.close",
            "nav.panel.collapse", "nav.panel.expand", "nav.store.context", "nav.store.context.exit", "nav.user.menu");

    private ResourceBundle bundle(String language) {
        return ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
    }

    @Test
    void translatesEveryMenuEntryInBothLanguages() {
        for (String language : List.of("pl", "en")) {
            // given
            ResourceBundle messages = bundle(language);

            // when / then
            NavigationCatalog.sections().forEach(section -> {
                assertThat(messages.containsKey(section.messageKey())).as(language + " " + section.messageKey()).isTrue();
                section.items().forEach(item ->
                        assertThat(messages.containsKey(item.messageKey())).as(language + " " + item.messageKey()).isTrue());
            });
            NavigationCatalog.footer().forEach(item ->
                    assertThat(messages.containsKey(item.messageKey())).as(language + " " + item.messageKey()).isTrue());
        }
    }

    @Test
    void translatesEveryNavigationControlInBothLanguages() {
        for (String language : List.of("pl", "en")) {
            // given
            ResourceBundle messages = bundle(language);

            // when / then
            CONTROL_KEYS.forEach(key ->
                    assertThat(messages.containsKey(key)).as(language + " " + key).isTrue());
        }
    }
}
