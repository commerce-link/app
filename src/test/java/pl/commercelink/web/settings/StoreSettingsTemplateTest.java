package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreForm;

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
        assertThat(html).contains("Fakturowanie").contains("System fakturowy i ustawienia faktur");
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
}
