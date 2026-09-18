package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsHeaderFragmentTest {

    private static final String WITHOUT_ACTIONS =
            "<div th:replace=\"~{fragments/settings-header :: header(false, null)}\"></div>";

    private Map<String, Object> page(UserRole role, String path) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(role, path));
        return variables;
    }

    @Test
    void titlesThePageLikeItsTileAndLinksBackToTheSettings() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS, page(UserRole.ADMIN, "/dashboard/store/warehouse"));

        // then
        assertThat(html).contains("class=\"cl-back\"").contains("href=\"/dashboard/store\"").contains(">Ustawienia<");
        assertThat(html).contains("<h1 class=\"cl-page-title\">Magazyn</h1>");
        assertThat(html).contains("Dokumenty magazynowe, adresy przyjęcia towaru i drukarki etykiet.");
        assertThat(html).doesNotContain("screen-intro-toggle").doesNotContain("cl-page-actions");
    }

    @Test
    void linksTheSuperAdminBackToTheSettingsOfTheStoreTheyWorkIn() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS,
                page(UserRole.SUPER_ADMIN, "/dashboard/store/store-1/invoicing"));

        // then
        assertThat(html).contains("href=\"/dashboard/store/store-1\"");
        assertThat(html).contains("<h1 class=\"cl-page-title\">Fakturowanie</h1>");
    }

    @Test
    void leavesTheBackLinkOutWhenThereIsNoSettingsHomeToReturnTo() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS,
                page(UserRole.SUPER_ADMIN, "/dashboard/store/rma-centers"));

        // then
        assertThat(html).doesNotContain("cl-back");
        assertThat(html).contains("<h1 class=\"cl-page-title\">Centra RMA</h1>");
        assertThat(html).doesNotContain("cl-settings-nav").doesNotContain("cl-settings-jump");
    }

    @Test
    void rendersNothingOutsideASettingsPage() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS, page(UserRole.ADMIN, "/dashboard/orders"));

        // then
        assertThat(html).doesNotContain("cl-page-header");
        assertThat(html).doesNotContain("cl-settings-nav").doesNotContain("cl-settings-jump");
    }

    @Test
    void showsTheHelpToggleAndThePageActionsWhenTheSubpageAsksForThem() {
        // when
        String html = SettingsTemplateRenderer.render("settings-header-with-actions-harness",
                page(UserRole.ADMIN, "/dashboard/store/rma-centers"));

        // then
        assertThat(html).contains("screen-intro-toggle");
        assertThat(html).contains("class=\"cl-page-actions\"");
        assertThat(html).contains("href=\"/dashboard/store/rma-centers/new\"").contains("Dodaj centrum");
        assertThat(html.split("Dodaj centrum", -1)).hasSize(2);
    }
}
