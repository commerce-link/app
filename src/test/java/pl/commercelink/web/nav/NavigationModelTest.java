package pl.commercelink.web.nav;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationModelTest {

    private List<String> itemKeys(NavigationModel model) {
        return model.sections().stream().flatMap(section -> section.items().stream()).map(NavItem::key).toList();
    }

    @Test
    void showsTheAdminEverySectionIncludingFinanceAndSettings() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/orders");

        // then
        assertThat(itemKeys(model)).contains("offers", "orders", "clients", "fulfilment", "deliveries", "rma",
                "warehouse", "warehouseDocuments", "catalogs", "inventory", "payments", "reports");
        assertThat(model.footer()).extracting(NavItem::key).containsExactly("settings");
    }

    @Test
    void hidesAdminOnlyDestinationsFromTheUser() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.USER, "/dashboard/orders");

        // then
        assertThat(itemKeys(model)).doesNotContain("payments", "reports", "catalogs", "fulfilment");
        assertThat(model.footer()).isEmpty();
    }

    @Test
    void givesTheSuperAdminTheQueuesAndTheAdministrationGroupOnly() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.SUPER_ADMIN, "/dashboard/stores");

        // then
        assertThat(itemKeys(model)).containsExactly("fulfilmentQueue", "deliveriesQueue", "inventory",
                "stores", "rmaCenters");
        assertThat(itemKeys(model)).doesNotContain("offers", "orders");
    }

    @Test
    void dropsGroupsThatTheRoleWouldSeeEmpty() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.SUPER_ADMIN, "/dashboard/stores");

        // then
        assertThat(model.sections()).extracting(NavSection::messageKey)
                .containsExactly("nav.group.fulfilment", "nav.group.catalog", "nav.group.administration");
    }

    @Test
    void picksTheLongestMatchingPathSoDocumentsDoNotLightUpTheWarehouse() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/warehouse-documents");

        // then
        assertThat(model.active().key()).isEqualTo("warehouseDocuments");
    }

    @Test
    void keepsTheSectionActiveOnDeeperPaths() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/rma/new");

        // then
        assertThat(model.active().key()).isEqualTo("rma");
        assertThat(model.activeSectionMessageKey()).isEqualTo("nav.group.fulfilment");
        assertThat(model.activeMessageKey()).isEqualTo("nav.rma");
    }

    @Test
    void matchesTheSettingsEntryFromTheFooterOnItsSubPages() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/store/shipping");

        // then
        assertThat(model.active().key()).isEqualTo("settings");
    }

    @Test
    void resolvesStoreScopedSuperAdminPathsAgainstTheGlobalEntries() {
        // when
        NavigationModel model =
                NavigationModel.forRoleAndPath(UserRole.SUPER_ADMIN, "/dashboard/store/uma2dqukxr/deliveries");

        // then
        assertThat(model.active().key()).isEqualTo("deliveriesQueue");
    }

    @Test
    void leavesNothingActiveOnAPathOutsideTheMenu() {
        // when
        NavigationModel model = NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/item-history");

        // then
        assertThat(model.active()).isNull();
        assertThat(model.activeSectionMessageKey()).isNull();
        assertThat(model.activeMessageKey()).isNull();
    }
}
