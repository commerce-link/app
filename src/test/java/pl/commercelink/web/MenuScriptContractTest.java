package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared action menu script (static/js/menu.js), read as source: the repository has no JavaScript test runner. */
class MenuScriptContractTest {

    private static String script() throws Exception {
        return Files.readString(Path.of("src/main/resources/static/js/menu.js"), StandardCharsets.UTF_8);
    }

    @Test
    void homeAndEndJumpToTheFirstAndLastUsableItem() throws Exception {
        // when
        String js = script();

        // then
        assertThat(js).contains("event.key === 'Home' || event.key === 'End'")
                .contains("usable[event.key === 'Home' ? 0 : usable.length - 1].focus()");
    }

    @Test
    void usableItemsSkipDisabledAndHiddenEntries() throws Exception {
        // when
        String js = script();

        // then — an entry a script has not revealed ([hidden] on its li) must not take the focus
        assertThat(js).contains(".cl-menu-item:not([aria-disabled=\"true\"])").contains("!item.closest('[hidden]')");
    }

    @Test
    void arrowsEscapeAndTabKeepTheirBehaviour() throws Exception {
        // when
        String js = script();

        // then
        assertThat(js).contains("event.key === 'ArrowDown' || event.key === 'ArrowUp'").contains("event.key === 'Escape'")
                .contains("event.key === 'Tab'");
    }
}
