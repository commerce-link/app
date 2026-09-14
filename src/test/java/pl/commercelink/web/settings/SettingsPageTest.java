package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;

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
}
