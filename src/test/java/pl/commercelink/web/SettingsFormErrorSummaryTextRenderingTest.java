package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsFormErrorSummaryTextRenderingTest {

    private static String render(String fragment) {
        Context context = new Context();
        context.setVariable("errors", Map.of("catalog", "Choose a catalog."));
        return EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{" + fragment + "}\"></div>", context)
                .replaceAll(">\\s+<", "><").trim();
    }

    @Test
    void theTextSummaryKeepsTheSavedNothingTitleOfTheSettingsForms() {
        // when
        String html = render("fragments/settings-form :: errorSummaryText('x-errors', ${errors})");

        // then
        assertThat(html).contains("<p class=\"cl-alert-title\">Changes were not saved. Correct the highlighted fields:</p>")
                .contains("<a href=\"#catalog\">Choose a catalog.</a>");
    }

    @Test
    void theTitledTextSummaryRendersLikeTheTextSummaryWithItsOwnTitle() {
        // when
        String titled = render("fragments/settings-form :: errorSummaryTextTitled('x-errors', ${errors}, 'form.errors.title.neutral')");
        String plain = render("fragments/settings-form :: errorSummaryText('x-errors', ${errors})");

        // then
        assertThat(titled).contains("<p class=\"cl-alert-title\">Correct the highlighted fields:</p>")
                .isEqualTo(plain.replace("Changes were not saved. Correct the highlighted fields:", "Correct the highlighted fields:"));
    }
}
