package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySettingsTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/category-settings.html"), StandardCharsets.UTF_8);
    }

    /** The page as the hub renders it after a save that also left a warning. */
    private static String rendered(String warning) {
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        Context context = new Context();
        context.setVariable("category", gpu);
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1");
        context.setVariable("basicsHref", "/dashboard/catalogs/c1/category/k1/settings/basics");
        context.setVariable("pricingHref", "/dashboard/catalogs/c1/category/k1/settings/pricing");
        context.setVariable("marketplacesHref", "/dashboard/catalogs/c1/category/k1/settings/marketplaces");
        context.setVariable("filtersHref", "/dashboard/catalogs/c1/category/k1/settings/filters");
        context.setVariable("settingsSavedMessage", "Saved");
        context.setVariable("catalogWarning", warning);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-settings", context);
    }

    @Test
    void theHubShowsTheWarningOfTheSaveThatReturnedToIt() {
        // when
        String html = rendered("No PIM category chosen");

        // then
        assertThat(html).contains("cl-alert is-warn").contains("No PIM category chosen");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void theHubShowsNoWarningBoxWhenTheSaveLeftNone() {
        // when
        String html = rendered(null);

        // then
        assertThat(html).contains("Saved").doesNotContain("cl-alert is-warn");
    }

    @Test
    void theHubHasNoLeadUnderItsTitle() {
        // when
        String html = rendered(null);

        // then
        assertThat(html).doesNotContain("cl-page-lead").doesNotContain("Four short forms");
    }

    /** The category is created from the Basics page, so its own page carries the same warning after the redirect. */
    @Test
    void theCategoryPageCarriesTheSameWarningFragment() throws Exception {
        // given
        String categoryPage = Files.readString(Path.of("src/main/resources/templates/catalog/category.html"),
                StandardCharsets.UTF_8);

        // then
        assertThat(page()).contains("settings-form :: warnAlert");
        assertThat(categoryPage).contains("settings-form :: warnAlert");
    }
}
