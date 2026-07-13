package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebControllerWelcomeTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private WebController controller;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    private static Store storeWithWelcome() {
        Store store = new Store();
        store.getNotifications().add(new StoreNotification(
                StoreNotificationSeverity.INFO, StoreNotificationType.WELCOME, null, null));
        return store;
    }

    private Store givenCurrentStore(Store store) {
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
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

    @Test
    void dismissRemovesWelcomeNotificationAndSavesStore() {
        // given
        Store store = givenCurrentStore(storeWithWelcome());

        // when
        String view = controller.dismissWelcome();

        // then
        assertTrue(store.getNotifications().isEmpty());
        verify(storesRepository).save(store);
        assertEquals("redirect:/dashboard", view);
    }

    @Test
    void dismissIsIdempotentAndSkipsSaveWhenNothingRemoved() {
        // given
        givenCurrentStore(new Store());

        // when
        controller.dismissWelcome();

        // then
        verify(storesRepository, never()).save(any());
    }

    @Test
    void dismissRemovesOnlyWelcomeNotifications() {
        // given
        StoreNotification unauthenticated = new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "morele", "token expired");
        Store store = storeWithWelcome();
        store.getNotifications().add(unauthenticated);
        givenCurrentStore(store);

        // when
        controller.dismissWelcome();

        // then
        assertEquals(List.of(unauthenticated), store.getNotifications());
        verify(storesRepository).save(store);
    }

    @Test
    void dismissNoOpsForLegacyStoreWithoutNotifications() {
        // given
        Store store = new Store();
        store.setNotifications(null);
        givenCurrentStore(store);

        // when
        String view = controller.dismissWelcome();

        // then
        assertEquals("redirect:/dashboard", view);
        verify(storesRepository, never()).save(any());
    }
}
