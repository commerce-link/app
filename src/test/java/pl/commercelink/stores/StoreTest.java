package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StoreTest {

    @Test
    void returnsConfiguredInventoryCacheTtl() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setInventoryCacheTtlMinutes(30);
        Store store = new Store();
        store.setFulfilmentConfiguration(config);

        // when
        Optional<Integer> ttl = store.getInventoryCacheTtlMinutes();

        // then
        assertThat(ttl).contains(30);
    }

    @Test
    void returnsEmptyTtlWhenNotConfigured() {
        // given
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());

        // when
        Optional<Integer> ttl = store.getInventoryCacheTtlMinutes();

        // then
        assertThat(ttl).isEmpty();
    }

    @Test
    void returnsEmptyTtlWhenNoFulfilmentConfiguration() {
        // given
        Store store = new Store();

        // when
        Optional<Integer> ttl = store.getInventoryCacheTtlMinutes();

        // then
        assertThat(ttl).isEmpty();
    }

    @Test
    void returnsConfiguredEnabledCategories() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledCategories(List.of("Dom", "Biuro"));
        Store store = new Store();
        store.setFulfilmentConfiguration(config);

        // when
        List<String> enabledCategories = store.getEnabledCategories();

        // then
        assertThat(enabledCategories).containsExactly("Dom", "Biuro");
    }

    @Test
    void returnsNoEnabledCategoriesWhenNotConfigured() {
        // given
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());

        // when
        List<String> enabledCategories = store.getEnabledCategories();

        // then
        assertThat(enabledCategories).isEmpty();
    }

    @Test
    void returnsNoEnabledCategoriesWhenNoFulfilmentConfiguration() {
        // given
        Store store = new Store();

        // when
        List<String> enabledCategories = store.getEnabledCategories();

        // then
        assertThat(enabledCategories).isEmpty();
    }

    @Test
    void markConnectionAsLostFlagsTheMarketplaceAsDisconnected() {
        // given
        Store store = new Store();
        store.getMarketplaces().add(new MarketplaceIntegration("Allegro"));

        // when
        store.markConnectionAsLost("Allegro");

        // then
        assertThat(store.getMarketplaceIntegration("Allegro").isLoggedIn()).isFalse();
    }

    @Test
    void markConnectionAsRestoredFlagsTheMarketplaceAsConnectedAgain() {
        // given
        Store store = new Store();
        MarketplaceIntegration integration = new MarketplaceIntegration("Allegro");
        integration.setLoggedIn(false);
        store.getMarketplaces().add(integration);

        // when
        store.markConnectionAsRestored("Allegro");

        // then
        assertThat(store.getMarketplaceIntegration("Allegro").isLoggedIn()).isTrue();
    }
}
