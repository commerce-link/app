package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSettingsOverviewFactoryTest {

    private final StoreSettingsOverviewFactory factory = new StoreSettingsOverviewFactory();

    private Store emptyStore() {
        Store store = new Store();
        store.setStoreId("store-1");
        return store;
    }

    private List<SettingsTileView> tiles(StoreSettingsOverview overview) {
        return overview.sections().stream().flatMap(section -> section.tiles().stream()).toList();
    }

    private SettingsTileView tile(StoreSettingsOverview overview, String key) {
        return tiles(overview).stream().filter(view -> view.tile().key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void buildsEverySectionForTheStoreAdminAndLinksTilesToTheirOwnSettingsPages() {
        // when
        StoreSettingsOverview overview = factory.build(emptyStore(), UserRole.ADMIN);

        // then
        assertThat(overview.sections()).hasSize(6);
        assertThat(tiles(overview)).hasSize(16);
        assertThat(tile(overview, "warehouse").href()).isEqualTo("/dashboard/store/warehouse");
        assertThat(tile(overview, "rmaCenters").href()).isEqualTo("/dashboard/store/rma-centers");
    }

    @Test
    void hidesRmaCentersFromTheSuperAdminAndPrefixesLinksWithTheStore() {
        // when
        StoreSettingsOverview overview = factory.build(emptyStore(), UserRole.SUPER_ADMIN);

        // then
        assertThat(tiles(overview)).hasSize(15);
        assertThat(tiles(overview)).noneMatch(view -> view.tile().key().equals("rmaCenters"));
        assertThat(tile(overview, "warehouse").href()).isEqualTo("/dashboard/store/store-1/warehouse");
    }

    @Test
    void leavesTheStoreUntouched() {
        // given
        Store store = emptyStore();

        // when
        factory.build(store, UserRole.ADMIN);

        // then
        assertThat(store.getBranding()).isNull();
        assertThat(store.getWarehouseConfiguration()).isNull();
        assertThat(store.getClientNotificationsConfiguration()).isNull();
        assertThat(store.getCheckoutConfiguration()).isNull();
    }
}
