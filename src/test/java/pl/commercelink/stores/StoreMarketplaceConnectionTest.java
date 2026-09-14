package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreMarketplaceConnectionTest {

    private static Store storeWith(String marketplace, boolean loggedIn, String schedule) {
        Store store = new Store();
        store.setStoreId("store-1");
        MarketplaceIntegration integration = new MarketplaceIntegration(marketplace);
        integration.setLoggedIn(loggedIn);
        integration.setOrdersImportSchedule(schedule);
        store.getMarketplaces().add(integration);
        return store;
    }

    @Test
    void aStoreWithoutItsOwnScheduleImportsOnTheGlobalOne() {
        assertThat(storeWith("Allegro", true, null).importsOrdersOnGlobalSchedule("Allegro")).isTrue();
        assertThat(storeWith("Allegro", true, "  ").importsOrdersOnGlobalSchedule("Allegro")).isTrue();
    }

    @Test
    void aStoreWithItsOwnScheduleIsLeftToItsOwnSchedule() {
        assertThat(storeWith("Allegro", true, "0/15 * * * ? *").importsOrdersOnGlobalSchedule("Allegro")).isFalse();
    }

    @Test
    void aLoggedOutOrForeignIntegrationNeverImportsGlobally() {
        assertThat(storeWith("Allegro", false, null).importsOrdersOnGlobalSchedule("Allegro")).isFalse();
        assertThat(storeWith("Empik", true, null).importsOrdersOnGlobalSchedule("Allegro")).isFalse();
    }

    @Test
    void connectingANewMarketplaceStartsLoggedInUnlessItNeedsDeviceAuthorization() {
        Store store = new Store();

        assertThat(store.connectMarketplace("Empik", false).isLoggedIn()).isTrue();
        assertThat(store.connectMarketplace("Allegro", true).isLoggedIn()).isFalse();
        assertThat(store.getMarketplaces()).hasSize(2);
    }

    @Test
    void reconnectingAManualTokenMarketplaceRestoresALostConnection() {
        Store store = storeWith("Empik", false, "0/15 * * * ? *");

        MarketplaceIntegration integration = store.connectMarketplace("Empik", false);

        assertThat(integration.isLoggedIn()).isTrue();
        assertThat(integration.getOrdersImportSchedule()).isEqualTo("0/15 * * * ? *");
        assertThat(store.getMarketplaces()).hasSize(1);
    }

    @Test
    void reconnectingADeviceAuthMarketplaceDoesNotFakeARestoredConnection() {
        Store store = storeWith("Allegro", false, null);

        assertThat(store.connectMarketplace("Allegro", true).isLoggedIn()).isFalse();
        assertThat(store.getMarketplaces()).hasSize(1);
    }
}
