package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

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
}
