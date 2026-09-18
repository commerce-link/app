package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSettingsTemplateTest {

    private static final String SECTIONS = "<div th:replace=\"~{fragments/store-settings :: sections(${sections})}\"></div>";

    private SettingsTileView view(String relativePath, String href) {
        return new SettingsTileView(StoreSettingsCatalog.tileAt(relativePath).orElseThrow(), href);
    }

    private List<SettingsSectionView> sections() {
        return List.of(
                new SettingsSectionView("store.settings.group.finance", List.of(
                        view("/invoicing", "/dashboard/store/invoicing"),
                        view("/payments", "/dashboard/store/payments"))),
                new SettingsSectionView("store.settings.group.returns", List.of(
                        view("/rma-centers", "/dashboard/store/rma-centers"))));
    }

    @Test
    void rendersEverySectionWithItsHeadingAndLinksEachTileToItsPage() {
        // when
        String html = SettingsTemplateRenderer.render(SECTIONS, Map.of("sections", sections()));

        // then
        assertThat(html).contains("Finanse").contains("Zwroty");
        assertThat(html).contains("href=\"/dashboard/store/invoicing\"");
        assertThat(html).contains("href=\"/dashboard/store/rma-centers\"");
        assertThat(html).contains("Fakturowanie").contains("System fakturowy, warunki faktur i konta do przelewów");
        assertThat(html).contains("fa-calculator");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void rendersTilesWithoutAnyConfigurationStatusLabel() {
        // when
        String html = SettingsTemplateRenderer.render(SECTIONS, Map.of("sections", sections()));

        // then
        assertThat(html).doesNotContain("cl-status");
    }

    @Test
    void labelsEverySectionByItsHeadingForScreenReaders() {
        // when
        String html = SettingsTemplateRenderer.render(SECTIONS, Map.of("sections", sections()));

        // then
        assertThat(html).contains("aria-labelledby=\"settings-group-0\"").contains("id=\"settings-group-0\"");
        assertThat(html).contains("aria-labelledby=\"settings-group-1\"").contains("id=\"settings-group-1\"");
    }

    @Test
    void rendersTheWholeSuperAdminPageInsideTheLayout() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Sklep Demo");
        Map<String, Object> variables = new HashMap<>();
        variables.put("form", new StoreForm(store));
        variables.put("isSuperAdmin", true);
        variables.put("overview", new StoreSettingsOverview(sections()));
        variables.put("navigation", null);

        // when
        String html = SettingsTemplateRenderer.render("store", variables);

        // then
        assertThat(html).contains("Ustawienia sklepu: Sklep Demo");
        assertThat(html).contains("href=\"/dashboard/store/store-1/copy\"");
        assertThat(html).contains("cl-tile-grid");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void dropsTheOldCardGridItsBrokenIconsAndTheRawNotificationTable() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/store.html"), StandardCharsets.UTF_8);

        // then
        assertThat(template).doesNotContain("button is-primary").doesNotContain("card-content")
                .doesNotContain("fa-palette").doesNotContain("fa-file-invoice").doesNotContain("fa-store")
                .doesNotContain("store.notification.severity")
                .doesNotContain("store-settings :: alerts");
        assertThat(template).contains("fragments/screen-intro :: panel('store', 'fas fa-cog')");
    }

    @Test
    void keepsTheTileColumnNoWiderThanAPhonesContentBoxAndNeverFormsAFourthColumnOnWideScreens() throws Exception {
        // when
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);

        // then
        // The minimum tile width must both fit a phone's content box (17.5rem, or full
        // width below that) and never drop under a third of the row, otherwise a wide
        // screen fits a fourth column and every section is left half-empty.
        assertThat(css).contains("minmax(max(min(17.5rem, 100%), calc((100% - 32px) / 3)), 1fr)");
        assertThat(css).doesNotContain("minmax(17.5rem, 1fr)");
        assertThat(css).doesNotContain("minmax(min(17.5rem, 100%), 1fr)");
    }

    @Test
    void widensThePageBodyOnTheSettingsHomePageOnlySoWideScreensFitAThirdTileColumn() throws Exception {
        // given
        String template = Files.readString(Path.of("src/main/resources/templates/store.html"), StandardCharsets.UTF_8);
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);

        // then
        assertThat(template).contains("cl-page-body is-wide");
        assertThat(css).contains(".cl-page-body.is-wide {").contains("max-width: 1440px;");
    }

    @Test
    void pinsCrossDocumentViewTransitionsForTheShell() throws Exception {
        // when
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);

        // then
        assertThat(css).contains("@view-transition { navigation: auto; }");
        assertThat(css).contains("@view-transition { navigation: none; }");
        assertThat(css).contains("::view-transition-group(*), ::view-transition-old(*), ::view-transition-new(*) { animation-duration: 150ms; }");
    }
}
