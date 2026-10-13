package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.StoreCreateForm;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCreateTemplateRenderingTest {

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private String render(StoreCreateForm form, Map<String, String> errors, String formError, String back) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("form", form);
        context.setVariable("errors", errors);
        context.setVariable("formError", formError);
        context.setVariable("back", back);
        context.setVariable("backHref", back == null ? "/dashboard/stores" : back);
        return engine.process("store-create", context);
    }

    @Test
    void emptyFormIsADesignSystemSubpage() {
        // when
        String html = render(StoreCreateForm.withId("abcde12345"), Map.of(), null, null);

        // then
        assertThat(html).contains("cl-page-body is-form").contains("class=\"cl-back\" href=\"/dashboard/stores\"");
        assertThat(html.split("<h1").length - 1).isEqualTo(1);
        assertThat(html).contains(">New store<").contains("name=\"newStoreId\" value=\"abcde12345\"");
        assertThat(html).contains("for=\"name\"").contains("for=\"apiKey\"").contains("class=\"cl-optional\"");
        assertThat(html).contains("id=\"apiKey-help\"").contains("only its last 6 characters are saved");
        assertThat(html).contains("data-cl-submit-once").contains(">Create store<");
        assertThat(html).doesNotContain("class=\"title\"").doesNotContain("is-success").doesNotContain("class=\"notification");
        assertThat(html).doesNotContain("cl-alert is-bad");
    }

    @Test
    void errorsAreSummarisedAndMarkedAtTheFields() {
        // given
        StoreCreateForm form = StoreCreateForm.withId("abcde12345");
        form.setName(" ");
        form.setApiKey("ab1");

        // when
        String html = render(form, Map.of("name", "store.create.error.name.required", "apiKey", "store.create.error.apiKey.short"),
                null, "/dashboard/stores?status=trial");

        // then
        assertThat(html).contains("id=\"store-create-errors\"").contains("href=\"#name\"").contains("href=\"#apiKey\"");
        assertThat(html).contains("aria-invalid=\"true\"").contains("value=\"ab1\"");
        assertThat(html).contains("name=\"back\" value=\"/dashboard/stores?status=trial\"");
    }

    @Test
    void failedSaveShowsTheAlertWithoutAFieldList() {
        // when
        String html = render(StoreCreateForm.withId("abcde12345"), Map.of(), "store.create.error", null);

        // then
        assertThat(html).contains("id=\"store-create-errors\"").contains("The store could not be created. Try again.");
        assertThat(html).doesNotContain("cl-alert-list");
    }
}
