package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsPageTest {

    @Test
    void matchesTheStoreAdminSettingsPageAndPointsBackToTheirSettings() {
        // when
        SettingsPage page = SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/warehouse");

        // then
        assertThat(page.tile().key()).isEqualTo("warehouse");
        assertThat(page.homeHref()).isEqualTo("/dashboard/store");
    }

    @Test
    void matchesTheSuperAdminSettingsPageOfAStoreAndPointsBackToThatStore() {
        // when
        SettingsPage page = SettingsPage.forRequest(UserRole.SUPER_ADMIN, "/dashboard/store/store-1/email-templates");

        // then
        assertThat(page.tile().key()).isEqualTo("emailTemplates");
        assertThat(page.homeHref()).isEqualTo("/dashboard/store/store-1");
    }

    @Test
    void keepsTheTitleButDropsTheBackLinkOnTheSharedRmaCentresOfTheSuperAdmin() {
        // when
        SettingsPage page = SettingsPage.forRequest(UserRole.SUPER_ADMIN, "/dashboard/store/rma-centers");

        // then
        assertThat(page.tile().key()).isEqualTo("rmaCenters");
        assertThat(page.homeHref()).isNull();
    }

    @Test
    void ignoresDeeperPagesTheHomePageAndPagesOutsideTheSettings() {
        // when / then
        assertThat(SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/rma-centers/new")).isNull();
        assertThat(SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store")).isNull();
        assertThat(SettingsPage.forRequest(UserRole.SUPER_ADMIN, "/dashboard/store/store-1")).isNull();
        assertThat(SettingsPage.forRequest(UserRole.SUPER_ADMIN, "/dashboard/store/store-1/copy")).isNull();
        assertThat(SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/orders")).isNull();
        assertThat(SettingsPage.forRequest(null, "/dashboard/store/warehouse")).isNull();
    }

    @Test
    void doesNotOfferAStoreAdminTheSettingsPagesOfAnotherStore() {
        // when / then
        assertThat(SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/store-1/warehouse")).isNull();
    }

    @Test
    void buildsTheSettingsPageForAStoreAdminByTile() {
        // when
        SettingsPage page = SettingsPage.forTile("/shipping", UserRole.ADMIN, "store-1");

        // then
        assertThat(page.tile().key()).isEqualTo("shipping");
        assertThat(page.homeHref()).isEqualTo("/dashboard/store");
    }

    @Test
    void buildsTheSettingsPageForASuperAdminByTile() {
        // when
        SettingsPage page = SettingsPage.forTile("/shipping", UserRole.SUPER_ADMIN, "store-1");

        // then
        assertThat(page.tile().key()).isEqualTo("shipping");
        assertThat(page.homeHref()).isEqualTo("/dashboard/store/store-1");
    }

    @Test
    void fillsTheNavigationForAStoreAdminWithEverySectionAndTileIncludingRmaCenters() {
        // when
        SettingsPage page = SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/warehouse");

        // then
        assertThat(page.navigation()).hasSize(6);
        List<SettingsTileView> tiles = page.navigation().stream().flatMap(section -> section.tiles().stream()).toList();
        assertThat(tiles).hasSize(14);
        assertThat(tiles).extracting(tile -> tile.tile().key()).contains("rmaCenters");
        assertThat(tiles).allMatch(tile -> tile.href().startsWith("/dashboard/store/"));
    }

    @Test
    void fillsTheNavigationForASuperAdminInAStoreWithoutRmaCentersAndPrefixesHrefsWithTheStore() {
        // when
        SettingsPage page = SettingsPage.forRequest(UserRole.SUPER_ADMIN, "/dashboard/store/store-1/invoicing");

        // then
        List<SettingsTileView> tiles = page.navigation().stream().flatMap(section -> section.tiles().stream()).toList();
        assertThat(tiles).hasSize(13);
        assertThat(tiles).noneMatch(tile -> tile.tile().key().equals("rmaCenters"));
        assertThat(tiles).allMatch(tile -> tile.href().startsWith("/dashboard/store/store-1/"));
    }

    @Test
    void leavesTheNavigationEmptyOnTheSharedRmaCentresOfTheSuperAdmin() {
        // when
        SettingsPage page = SettingsPage.forRequest(UserRole.SUPER_ADMIN, "/dashboard/store/rma-centers");

        // then
        assertThat(page.navigation()).isEmpty();
    }

    @Test
    void fillsTheNavigationWhenBuiltByTile() {
        // when
        SettingsPage page = SettingsPage.forTile("/shipping", UserRole.ADMIN, "store-1");

        // then
        assertThat(page.navigation()).hasSize(6);
        assertThat(page.navigation().stream().flatMap(section -> section.tiles().stream())).hasSize(14);
    }

    @Test
    void fillsTheNavigationForASuperAdminWhenBuiltByTileWithHrefsPrefixedByTheStore() {
        // when
        SettingsPage page = SettingsPage.forTile("/shipping", UserRole.SUPER_ADMIN, "store-1");

        // then
        List<SettingsTileView> tiles = page.navigation().stream().flatMap(section -> section.tiles().stream()).toList();
        assertThat(tiles).hasSize(13);
        assertThat(tiles).noneMatch(tile -> tile.tile().key().equals("rmaCenters"));
        assertThat(tiles).allMatch(tile -> tile.href().startsWith("/dashboard/store/store-1/"));
    }
}
