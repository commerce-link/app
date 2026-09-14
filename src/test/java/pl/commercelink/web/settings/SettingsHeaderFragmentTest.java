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
        assertThat(html).contains("Konfiguruj ustawienia magazynu i adresy wysyłkowe");
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
    }

    @Test
    void leavesOutTheNavigationAndJumpMenuWhenThereIsNoSettingsHomeToReturnTo() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS,
                page(UserRole.SUPER_ADMIN, "/dashboard/store/rma-centers"));

        // then
        assertThat(html).doesNotContain("cl-settings-nav").doesNotContain("cl-settings-jump");
    }

    @Test
    void rendersTheNavigationGroupedBySectionWithAllTileHrefsForAStoreAdmin() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS, page(UserRole.ADMIN, "/dashboard/store/warehouse"));

        // then
        assertThat(html).contains("<nav class=\"cl-settings-nav\"").contains("aria-label=\"Ustawienia sklepu\"");
        String navBlock = navBlock(html);
        assertThat(occurrences(navBlock, "cl-settings-nav-title")).isEqualTo(6);
        assertThat(navBlock).contains("href=\"/dashboard/store/rma-centers\"").contains(">Centra RMA<");
        assertThat(navBlock).contains("href=\"/dashboard/store/invoicing\"");
    }

    @Test
    void rendersTheNavigationPrefixedWithTheStoreForASuperAdminAndWithoutRmaCenters() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS,
                page(UserRole.SUPER_ADMIN, "/dashboard/store/store-1/invoicing"));

        // then
        assertThat(html).contains("href=\"/dashboard/store/store-1/warehouse\"");
        assertThat(html).doesNotContain("rma-centers");
    }

    @Test
    void marksExactlyTheCurrentTileAsActiveOnceInTheNavigationAndOnceInTheJumpMenu() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS, page(UserRole.ADMIN, "/dashboard/store/warehouse"));

        // then
        assertThat(occurrences(html, "aria-current=\"page\"")).isEqualTo(2);
        assertThat(occurrences(html, "cl-settings-nav-item is-active")).isEqualTo(2);
        assertThat(navBlock(html)).contains("aria-current=\"page\"");
        assertThat(detailsBlock(html)).contains("aria-current=\"page\"");
    }

    @Test
    void showsTheJumpMenuWithATranslatedSummaryBelowTheHeader() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS, page(UserRole.ADMIN, "/dashboard/store/warehouse"));

        // then
        assertThat(html).contains("<details class=\"cl-settings-jump\">");
        assertThat(html).contains("class=\"cl-settings-jump-toggle\"").contains("Przejdź do innego ustawienia");
        assertThat(html.indexOf("</header>")).isLessThan(html.indexOf("<details"));
    }

    @Test
    void placesTheNavigationAfterTheHeaderSoTabOrderFollowsThePageHeadingFirst() {
        // when
        String html = SettingsTemplateRenderer.render(WITHOUT_ACTIONS, page(UserRole.ADMIN, "/dashboard/store/warehouse"));

        // then: keyboard/tab order follows document order, so the heading and back link must come
        // before the settings navigation even though the grid keeps the nav visually on the side
        assertThat(html.indexOf("</header>")).isLessThan(html.indexOf("<nav class=\"cl-settings-nav\""));
        assertThat(html.indexOf("<nav class=\"cl-settings-nav\"")).isLessThan(html.indexOf("<details"));
    }

    private static String navBlock(String html) {
        return html.substring(html.indexOf("<nav class=\"cl-settings-nav\""), html.indexOf("<details"));
    }

    private static String detailsBlock(String html) {
        return html.substring(html.indexOf("<details"));
    }

    private static int occurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
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
