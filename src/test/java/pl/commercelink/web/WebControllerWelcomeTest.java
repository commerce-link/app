package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void welcomeShownOnFirstDashboardVisitAndConsumed() {
        // given
        Store store = givenCurrentStore(storeWithWelcome());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.index(model);

        // then
        assertEquals("dashboard", view);
        assertEquals(true, model.getAttribute("welcomeMessage"));
        assertTrue(store.getNotifications().isEmpty());
        verify(storesRepository).save(store);
    }

    @Test
    void welcomeHiddenOnSecondDashboardVisit() {
        // given
        Store store = givenCurrentStore(storeWithWelcome());

        // when
        controller.index(new ExtendedModelMap());
        ExtendedModelMap secondVisit = new ExtendedModelMap();
        controller.index(secondVisit);

        // then
        assertEquals(false, secondVisit.getAttribute("welcomeMessage"));
        verify(storesRepository, times(1)).save(store);
    }

    @Test
    void welcomeConsumptionKeepsOtherNotifications() {
        // given
        StoreNotification unauthenticated = new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED, "morele", "token expired");
        Store store = storeWithWelcome();
        store.getNotifications().add(unauthenticated);
        givenCurrentStore(store);

        // when
        controller.index(new ExtendedModelMap());

        // then
        assertEquals(List.of(unauthenticated), store.getNotifications());
        verify(storesRepository).save(store);
    }

    @Test
    void welcomeHiddenWithoutNotificationAndNothingSaved() {
        // given
        givenCurrentStore(new Store());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.index(model);

        // then
        assertEquals(false, model.getAttribute("welcomeMessage"));
        verify(storesRepository, never()).save(any());
    }

    @Test
    void welcomeHiddenAndKeptOnDemoEnvironment() {
        // given
        controller.demoEnvironment = true;
        Store store = givenCurrentStore(storeWithWelcome());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.index(model);

        // then
        assertEquals(false, model.getAttribute("welcomeMessage"));
        assertEquals(1, store.getNotifications().size());
        verify(storesRepository, never()).save(any());
    }

    @Test
    void welcomeNoOpsForLegacyStoreWithoutNotifications() {
        // given
        Store store = new Store();
        store.setNotifications(null);
        givenCurrentStore(store);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.index(model);

        // then
        assertEquals("dashboard", view);
        assertEquals(false, model.getAttribute("welcomeMessage"));
        verify(storesRepository, never()).save(any());
    }
}
