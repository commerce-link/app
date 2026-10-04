package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.MarginConfiguration;
import pl.commercelink.web.dtos.MarginSettingsForm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreMarginsTemplateTest {

    private static String render(MarginSettingsForm form, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/margins"));
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("errorLabels", form.errorLabels(n -> "Kategoria " + n, n -> "Próg " + n));
        variables.put("categoryOptions", List.of(new PimCategoryOptions.CategoryOption("CPU", "CPU", null),
                new PimCategoryOptions.CategoryOption("Storage", "Storage", null)));
        variables.put("formAction", "/dashboard/store/margins");
        return SettingsTemplateRenderer.render("store-margins", variables);
    }

    @Test
    void thePageHasTheDefaultAndOneLinePerCategoryWithTheCatalogCategoriesSuggested() {
        // when
        String html = render(MarginSettingsForm.from(new MarginConfiguration(10.0,
                List.of(new MarginConfiguration.CategoryMargin("CPU", 5.0)))), Map.of());

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Marże</h1>")
                .containsPattern("id=\"defaultPercent\" name=\"defaultPercent\"\\s+value=\"10\"")
                .containsPattern("<input type=\"hidden\" name=\"categories\\[0\\]\\.category\" value=\"CPU\" data-picker-value>")
                .contains("aria-labelledby=\"category-0-label category-0-label-value\"")
                .contains("<span data-picker-label id=\"category-0-label-value\">CPU</span>")
                .contains("role=\"combobox\"").contains("aria-controls=\"category-0-label-listbox\"")
                .contains("id=\"category-0-category\"")
                .containsPattern("\"name\":\"Storage\"")
                .containsPattern("inputmode=\"decimal\"[^>]*id=\"category-0-percent\"\\s+name=\"categories\\[0\\]\\.percent\"\\s+value=\"5\"")
                .contains("aria-labelledby=\"margins-head-percent category-0-no\"")
                .contains("data-cl-repeat=\"categories\"").contains("Dodaj kategorię").contains("Zapisz zmiany")
                .doesNotContain("??");
    }

    @Test
    void anErrorIsNamedInTheSummaryWithItsRowAndShownAtItsField() {
        // given
        MarginSettingsForm form = MarginSettingsForm.from(null);
        form.getCategories().get(0).setCategory("CPU");

        // when
        String html = render(form, Map.of("category-0-percent", "store.margins.percent.required"));

        // then
        assertThat(html).contains("<a href=\"#category-0-percent\">Próg 1: Podaj próg.</a>")
                .containsPattern("id=\"category-0-percent\"[^>]*aria-invalid=\"true\"[^>]*aria-describedby=\"category-0-percent-error\"")
                .contains("id=\"category-0-percent-error\"");
    }
}
