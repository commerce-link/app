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
    void addNotificationSkipsAnAlreadyPresentDuplicate() {
        // given
        Store store = new Store();
        StoreNotification notification = new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "obj", "message");
        store.addNotification(notification);

        // when
        store.addNotification(new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "obj", "message"));

        // then
        assertThat(store.getNotifications()).hasSize(1);
    }

    @Test
    void addNotificationDropsTheOldestOnceTheCapIsExceeded() {
        // given: notifications have no dismiss path, so unbounded accumulation must be prevented
        Store store = new Store();
        for (int i = 0; i < 200; i++) {
            store.addNotification(new StoreNotification(
                    StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "obj-" + i, "message"));
        }

        // when
        store.addNotification(new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "obj-200", "message"));

        // then
        assertThat(store.getNotifications()).hasSize(200);
        assertThat(store.getNotifications().get(0).getObject()).isEqualTo("obj-1");
        assertThat(store.getNotifications().get(199).getObject()).isEqualTo("obj-200");
    }
}
