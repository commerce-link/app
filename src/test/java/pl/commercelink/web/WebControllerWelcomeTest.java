package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebControllerWelcomeTest {

    private static Store storeWithWelcome() {
        Store store = new Store();
        store.getNotifications().add(new StoreNotification(
                StoreNotificationSeverity.INFO, StoreNotificationType.WELCOME, null, null));
        return store;
    }

    @Test
    void welcomeVisibleWhenNotificationPresent() {
        // when / then
        assertTrue(WebController.welcomeVisible(storeWithWelcome(), false));
    }

    @Test
    void welcomeHiddenWithoutNotification() {
        // given
        Store store = new Store();

        // when / then
        assertFalse(WebController.welcomeVisible(store, false));
    }

    @Test
    void welcomeHiddenOnDemoEnvironment() {
        // when / then
        assertFalse(WebController.welcomeVisible(storeWithWelcome(), true));
    }

    @Test
    void welcomeIgnoresOtherNotificationTypes() {
        // given
        Store store = new Store();
        store.getNotifications().add(new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "morele", "token expired"));

        // when / then
        assertFalse(WebController.welcomeVisible(store, false));
    }
}
