package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A store ships through its default integration and any number of additional ones (Wysyłam z Allegro). */
class StoreShippingIntegrationsTest {

    private static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        return store;
    }

    @Test
    void theDefaultIntegrationComesFirstAndAdditionalOnesFollow() {
        // given
        Store store = store();
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.addAdditionalShippingIntegration("allegro");

        // when / then
        assertThat(store.defaultShippingIntegration()).isEqualTo("furgonetka");
        assertThat(store.shippingIntegrationNames()).containsExactly("furgonetka", "allegro");
        assertThat(store.hasShippingIntegration("allegro")).isTrue();
        assertThat(store.hasShippingIntegration("furgonetka")).isTrue();
        assertThat(store.hasShippingIntegration("inpost")).isFalse();
        assertThat(store.hasShippingIntegration(null)).isFalse();
    }

    @Test
    void anAdditionalIntegrationIsAddedOnceAndRemovedAlone() {
        // given
        Store store = store();
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");

        // when
        store.addAdditionalShippingIntegration("allegro");
        store.addAdditionalShippingIntegration("allegro");
        java.util.List<String> added = store.additionalShippingIntegrations();
        store.removeAdditionalShippingIntegration("allegro");

        // then
        assertThat(added).containsExactly("allegro");
        assertThat(store.additionalShippingIntegrations()).isEmpty();
        assertThat(store.defaultShippingIntegration()).isEqualTo("furgonetka");
    }

    @Test
    void aStoreWithOnlyTheAdditionalIntegrationHasNoDefault() {
        // given
        Store store = store();
        store.addAdditionalShippingIntegration("allegro");

        // when / then
        assertThat(store.defaultShippingIntegration()).isNull();
        assertThat(store.shippingIntegrationNames()).containsExactly("allegro");
    }

    @Test
    void aStoreWithoutShippingSettingsHasNoAdditionalIntegrationsAndGetsThemOnAdd() {
        // given: stores saved before the shipping settings existed have no ShippingConfiguration
        Store store = store();

        // when
        java.util.List<String> before = store.additionalShippingIntegrations();
        store.removeAdditionalShippingIntegration("allegro");
        store.addAdditionalShippingIntegration("allegro");

        // then
        assertThat(before).isEmpty();
        assertThat(store.getShippingConfiguration().getAdditionalIntegrations()).containsExactly("allegro");
    }

    @Test
    void aDefaultLostToAuthorisationReadsAsNotConnected() {
        // given: ShippingProviderFactory#onAuthorizationLost keeps the entry with a null name
        Store store = store();
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, null);

        // when / then
        assertThat(store.shippingIntegrationNames()).isEmpty();
    }
}
