package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.nav.StorePath;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSettingsCatalogTest {

    private List<SettingsTile> allTiles() {
        return StoreSettingsCatalog.sections().stream().flatMap(section -> section.tiles().stream()).toList();
    }

    @Test
    void listsTheSectionsInTheOrderANewStoreIsSetUp() {
        // when / then
        assertThat(StoreSettingsCatalog.sections()).extracting(SettingsSection::messageKey).containsExactly(
                "store.settings.group.company", "store.settings.group.finance", "store.settings.group.sales",
                "store.settings.group.fulfilment", "store.settings.group.returns", "store.settings.group.communication");
    }

    @Test
    void givesEveryTileAUniqueKeyIconAndPath() {
        // when
        List<SettingsTile> tiles = allTiles();

        // then
        assertThat(tiles).hasSize(16);
        assertThat(tiles).extracting(SettingsTile::key).doesNotHaveDuplicates();
        assertThat(tiles).extracting(SettingsTile::icon).doesNotHaveDuplicates();
        assertThat(tiles).extracting(SettingsTile::relativePath).doesNotHaveDuplicates();
    }

    @Test
    void showsRmaCentersOnlyToTheStoreAdmin() {
        // when
        SettingsTile rmaCenters = StoreSettingsCatalog.tileAt("/rma-centers").orElseThrow();

        // then
        assertThat(rmaCenters.visibleFor(UserRole.ADMIN)).isTrue();
        assertThat(rmaCenters.visibleFor(UserRole.SUPER_ADMIN)).isFalse();
        assertThat(allTiles()).filteredOn(tile -> !tile.key().equals("rmaCenters"))
                .allMatch(tile -> tile.visibleFor(UserRole.ADMIN) && tile.visibleFor(UserRole.SUPER_ADMIN));
        assertThat(allTiles()).noneMatch(tile -> tile.visibleFor(UserRole.USER));
    }

    @Test
    void findsATileOnlyByItsExactRelativePath() {
        // when / then
        assertThat(StoreSettingsCatalog.tileAt("/warehouse")).map(SettingsTile::key).contains("warehouse");
        assertThat(StoreSettingsCatalog.tileAt("/suppliers")).map(SettingsTile::key).contains("suppliers");
        assertThat(StoreSettingsCatalog.tileAt("/rma-centers/new")).isEmpty();
        assertThat(StoreSettingsCatalog.tileAt("")).isEmpty();
    }

    @Test
    void pointsTheSuperAdminAtTheSettingsOfTheStoreTheyWorkIn() {
        // when / then
        assertThat(StoreSettingsCatalog.homeHref(UserRole.ADMIN, "store-1")).isEqualTo("/dashboard/store");
        assertThat(StoreSettingsCatalog.homeHref(UserRole.SUPER_ADMIN, "store-1")).isEqualTo("/dashboard/store/store-1");
    }

    @Test
    void everyTilePathIsReservedInStorePathSoItIsNeverMistakenForAStoreId() {
        // when / then: a tile whose first path segment is not reserved would be read as a store id
        // by StorePath.storeIdIn, and the super admin's settings page for it would lose its header
        allTiles().forEach(tile -> assertThat(StorePath.storeIdIn(StoreSettingsCatalog.HOME_PATH + tile.relativePath()))
                .as(tile.key())
                .isNull());
    }

    @Test
    void sectionsForFiltersTilesByRoleAndBuildsHrefsUnderHomeHref() {
        // when
        List<SettingsSectionView> adminSections = StoreSettingsCatalog.sectionsFor(UserRole.ADMIN, "/dashboard/store");
        List<SettingsSectionView> superAdminSections =
                StoreSettingsCatalog.sectionsFor(UserRole.SUPER_ADMIN, "/dashboard/store/store-1");

        // then
        List<SettingsTileView> adminTiles = adminSections.stream().flatMap(section -> section.tiles().stream()).toList();
        assertThat(adminSections).hasSize(6);
        assertThat(adminTiles).hasSize(16);
        assertThat(adminTiles).filteredOn(tile -> tile.tile().key().equals("warehouse"))
                .extracting(SettingsTileView::href).containsExactly("/dashboard/store/warehouse");

        List<SettingsTileView> superAdminTiles =
                superAdminSections.stream().flatMap(section -> section.tiles().stream()).toList();
        assertThat(superAdminTiles).hasSize(15);
        assertThat(superAdminTiles).noneMatch(tile -> tile.tile().key().equals("rmaCenters"));
        assertThat(superAdminTiles).filteredOn(tile -> tile.tile().key().equals("warehouse"))
                .extracting(SettingsTileView::href).containsExactly("/dashboard/store/store-1/warehouse");
    }

    @Test
    void translatesEverySectionAndTileInBothLanguages() {
        for (String language : List.of("pl", "en")) {
            // given
            ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));

            // when / then
            StoreSettingsCatalog.sections().forEach(section -> {
                assertThat(messages.containsKey(section.messageKey())).as(language + " " + section.messageKey()).isTrue();
                section.tiles().forEach(tile -> {
                    assertThat(messages.containsKey(tile.titleKey())).as(language + " " + tile.titleKey()).isTrue();
                    assertThat(messages.containsKey(tile.descriptionKey())).as(language + " " + tile.descriptionKey()).isTrue();
                });
            });
        }
    }
}
