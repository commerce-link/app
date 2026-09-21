package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The sender choice is one group: a screen reader announced the same name twice and each option's description twice. */
class ShippingSenderTemplateTest {

    @Test
    void theRadiosSitInOneNamedGroupWithTheirDescriptionReadOnce() throws Exception {
        // when
        String html = Files.readString(Path.of("src/main/resources/templates/store-shipping-sender.html"), StandardCharsets.UTF_8);

        // then
        assertThat(html).containsOnlyOnce("<legend class=\"cl-visually-hidden\"").contains("<div class=\"cl-choice-group is-row\">");
        assertThat(html).doesNotContain("aria-describedby='sender-").doesNotContain("th:attr=\"aria-label=#{store.shipping.sender.title}\"");
        assertThat(html).contains("<fieldset class=\"cl-choice-group\" role=\"presentation\" data-cl-variant-when=\"sender=OTHER\">");
    }
}
