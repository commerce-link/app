package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.PimCategoryOptions.TopLevelChoice;
import pl.commercelink.starter.security.UserRole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCategoriesTemplateTest {

    private Map<String, Object> page(List<TopLevelChoice> choices) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/categories"));
        variables.put("navigation", null);
        variables.put("choices", choices);
        variables.put("selectedCount", choices.stream().filter(TopLevelChoice::selected).count());
        variables.put("formAction", "/dashboard/store/categories");
        return variables;
    }

    private String rendered(List<TopLevelChoice> choices) {
        return SettingsTemplateRenderer.render("store-categories", page(choices));
    }

    @Test
    void ticksTheSavedCategoriesAndPostsBackToThePage() {
        // when
        String html = rendered(List.of(new TopLevelChoice("Biuro", false, true), new TopLevelChoice("Dom", true, true)));

        // then
        assertThat(html).contains("action=\"/dashboard/store/categories\"");
        assertThat(html).contains("value=\"Dom\" checked");
        assertThat(html).contains("value=\"Biuro\"");
        assertThat(html).doesNotContain("value=\"Biuro\" checked");
        assertThat(html).doesNotContain("name=\"storeId\"");
    }

    /** Without the script the form falls back to a full page post, which is silent about why nothing improved. */
    @Test
    void loadsTheScriptThatSavesWithoutReloading() {
        // when
        String html = rendered(List.of(new TopLevelChoice("Dom", true, true)));

        // then
        assertThat(html).contains("data-cl-async");
        assertThat(html).contains("id=\"store-categories-form\"");
        assertThat(html).contains("/js/async-form.js");
    }

    @Test
    void warnsWhenNothingIsTickedBecauseTheCatalogPickersThenHaveNothingToOffer() {
        // when
        String warned = rendered(List.of(new TopLevelChoice("Dom", false, true)));
        String quiet = rendered(List.of(new TopLevelChoice("Dom", true, true)));

        // then
        assertThat(warned).contains("Żadna kategoria nie jest zaznaczona");
        assertThat(quiet).doesNotContain("Żadna kategoria nie jest zaznaczona");
    }

    @Test
    void marksACategoryTheCatalogueNoLongerOffers() {
        // when
        String html = rendered(List.of(new TopLevelChoice("Zniknięta", true, false), new TopLevelChoice("Dom", false, true)));

        // then
        assertThat(html).contains("Poza katalogiem PIM");
        assertThat(html).containsOnlyOnce("cl-status is-warn");
    }

    @Test
    void usesTheNewDesignInsteadOfBulmaBoxesAndButtons() {
        // when
        String html = rendered(List.of(new TopLevelChoice("Dom", true, true)));

        // then
        assertThat(html).contains("cl-card").contains("cl-check-columns").contains("cl-button is-primary");
        // The layout still ships Bulma markup of its own (boxes, modal buttons), so only the vocabulary this page
        // used to render is asserted away.
        assertThat(html).doesNotContain("columns is-multiline").doesNotContain("subtitle is-6")
                .doesNotContain("class=\"checkbox\"").doesNotContain("column is-one-third");
    }

    @Test
    void saysTheCategoryListIsUnavailableWhenPimReturnedNothing() {
        // when
        String html = rendered(List.of());

        // then
        assertThat(html).contains("Lista kategorii jest niedostępna");
        assertThat(html).doesNotContain("id=\"store-categories-form\"");
    }
}
