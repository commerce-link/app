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
    private static final String ALERTS = "<div th:replace=\"~{fragments/store-settings :: alerts(${alerts})}\"></div>";

    private SettingsTileView view(String relativePath, String href, TileStatus status) {
        return new SettingsTileView(StoreSettingsCatalog.tileAt(relativePath).orElseThrow(), href, status);
    }

    private List<SettingsSectionView> sections() {
        return List.of(
                new SettingsSectionView("store.settings.group.finance", List.of(
                        view("/invoicing", "/dashboard/store/invoicing",
                                TileStatus.ok("store.settings.status.connected", "Fakturownia")),
                        view("/payments", "/dashboard/store/payments",
                                TileStatus.neutral("store.settings.status.payments.none")))),
                new SettingsSectionView("store.settings.group.returns", List.of(
                        view("/rma-centers", "/dashboard/store/rma-centers", null))));
    }

    @Test
    void rendersEverySectionWithItsHeadingAndLinksEachTileToItsPage() {
        // when
        String html = SettingsTemplateRenderer.render(SECTIONS, Map.of("sections", sections()));

        // then
        assertThat(html).contains("Finanse").contains("Zwroty");
        assertThat(html).contains("href=\"/dashboard/store/invoicing\"");
        assertThat(html).contains("href=\"/dashboard/store/rma-centers\"");
        assertThat(html).contains("Fakturowanie").contains("Konfiguruj ustawienia fakturowania i integracje");
        assertThat(html).contains("fa-calculator");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void showsTheTranslatedStatusInThePillOfItsTone() {
        // when
        String html = SettingsTemplateRenderer.render(SECTIONS, Map.of("sections", sections()));

        // then
        assertThat(html).contains("class=\"cl-status is-ok\"");
        assertThat(html).contains("Połączono: Fakturownia");
        assertThat(html).contains("class=\"cl-status is-neutral\"");
        assertThat(html).contains("Brak bramki");
        assertThat(html.split("class=\"cl-status ", -1)).hasSize(3);
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
    void rendersNoAlertRegionWithoutNotifications() {
        // when
        String html = SettingsTemplateRenderer.render(ALERTS, Map.of("alerts", List.of()));

        // then
        assertThat(html).doesNotContain("cl-alert");
    }

    @Test
    void rendersEachAlertWithTitleOriginalMessageAndAction() {
        // given
        List<StoreAlert> alerts = List.of(
                new StoreAlert(true, "store.notification.type.UNAUTHENTICATED", "Your connection to Allegro expired",
                        "/dashboard/store/marketplaces", "store.notification.action.reconnect"),
                new StoreAlert(false, "store.notification.type.WELCOME", "Hello", null, null));

        // when
        String html = SettingsTemplateRenderer.render(ALERTS, Map.of("alerts", alerts));

        // then
        assertThat(html).contains("role=\"region\"").contains("aria-label=\"Powiadomienia sklepu\"");
        assertThat(html).contains("class=\"cl-alert is-warn\"").contains("class=\"cl-alert is-info\"");
        assertThat(html).contains("Wygasło połączenie z marketplace").contains("Your connection to Allegro expired");
        assertThat(html).contains("href=\"/dashboard/store/marketplaces\"").contains("Połącz ponownie");
        assertThat(html.split("cl-alert-action", -1)).hasSize(2);
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
        variables.put("overview", new StoreSettingsOverview(sections(), List.of()));
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
                .doesNotContain("store.notification.severity");
        assertThat(template).contains("fragments/screen-intro :: panel('store', 'fas fa-cog')");
    }

    @Test
    void keepsTheTileColumnNoWiderThanAPhonesContentBoxSoTilesDoNotOverflowOnNarrowScreens() throws Exception {
        // when
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);

        // then
        assertThat(css).contains("minmax(min(17.5rem, 100%), 1fr)");
        assertThat(css).doesNotContain("minmax(17.5rem, 1fr)");
    }
}
