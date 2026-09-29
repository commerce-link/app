package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders fragments/settings-form :: check through a real Thymeleaf engine: the checkbox is described by its
 * description and, when the page passes one, by the id of the field error it shows for the box.
 */
class SettingsFormCheckFragmentTest {

    private String render(String arguments) {
        return EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{fragments/settings-form :: check(" + arguments + ")}\"></div>", new Context());
    }

    @Test
    void withoutAnErrorTheCheckboxIsDescribedByItsDescriptionOnly() {
        String html = render("'enabled', 'store.receipts.enabled.label', true, 'store.receipts.enabled.description', null, null");

        assertThat(html).contains("aria-describedby=\"enabled-description\"");
        assertThat(html).contains("id=\"enabled-description\"");
    }

    @Test
    void anErrorIdIsAddedToTheDescription() {
        String html = render("'enabled', 'store.receipts.enabled.label', true, 'store.receipts.enabled.description', null, 'enabled-error'");

        assertThat(html).contains("aria-describedby=\"enabled-description enabled-error\"");
    }

    @Test
    void anErrorIdAloneDescribesTheCheckboxWhenThereIsNoDescription() {
        String html = render("'enabled', 'store.receipts.enabled.label', true, null, null, 'enabled-error'");

        assertThat(html).contains("aria-describedby=\"enabled-error\"");
        assertThat(html).doesNotContain("enabled-description");
    }
}
