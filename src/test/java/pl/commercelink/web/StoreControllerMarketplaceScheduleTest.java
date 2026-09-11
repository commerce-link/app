package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.marketplace.MarketplaceOrdersImportScheduler;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreControllerMarketplaceScheduleTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceOrdersImportScheduler ordersImportScheduler;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreController controller;

    private Store store;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "scheduleMinIntervalMinutes", 5);
        store = new Store();
        store.setStoreId("store-1");
        store.getMarketplaces().add(new MarketplaceIntegration("Allegro"));
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("msg");
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storesNormalizedCronAndAppliesSchedule() {
        // given
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.updateMarketplaceOrdersImportSchedule("store-1", "Allegro", "  0/15  * * * ? * ", Locale.ENGLISH, redirect);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces");
        assertThat(store.getMarketplaceIntegration("Allegro").getOrdersImportSchedule()).isEqualTo("0/15 * * * ? *");
        verify(ordersImportScheduler).apply("store-1", "Allegro", "0/15 * * * ? *");
        verify(storesRepository).save(store);
        verify(messageSource).getMessage(eq("store.marketplaces.import.schedule.updated"), any(), any(Locale.class));
        assertThat(redirect.getFlashAttributes()).containsKey("successMessage");
    }

    @Test
    void blankScheduleClearsAndRemovesTheSchedule() {
        // given
        store.getMarketplaceIntegration("Allegro").setOrdersImportSchedule("0/15 * * * ? *");

        // when
        controller.updateMarketplaceOrdersImportSchedule("store-1", "Allegro", "", Locale.ENGLISH, new RedirectAttributesModelMap());

        // then
        assertThat(store.getMarketplaceIntegration("Allegro").getOrdersImportSchedule()).isNull();
        verify(ordersImportScheduler).apply("store-1", "Allegro", null);
        verify(messageSource).getMessage(eq("store.marketplaces.import.schedule.cleared"), any(), any(Locale.class));
    }

    @Test
    void rejectsInvalidCronWithoutSaving() {
        // given
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.updateMarketplaceOrdersImportSchedule("store-1", "Allegro", "every 5 minutes", Locale.ENGLISH, redirect);

        // then
        verify(storesRepository, never()).save(any());
        verify(ordersImportScheduler, never()).apply(any(), any(), any());
        verify(messageSource).getMessage(eq("store.marketplaces.import.schedule.error.invalid"), any(), any(Locale.class));
        assertThat(redirect.getFlashAttributes()).containsKey("errorMessage");
    }

    @Test
    void rejectsCronBelowTheFloor() {
        // when
        controller.updateMarketplaceOrdersImportSchedule("store-1", "Allegro", "0/2 * * * ? *", Locale.ENGLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository, never()).save(any());
        verify(messageSource).getMessage(eq("store.marketplaces.import.schedule.error.too.frequent"), any(), any(Locale.class));
    }

    @Test
    void rejectsMarketplaceThatIsNotConnected() {
        // when
        controller.updateMarketplaceOrdersImportSchedule("store-1", "Empik", "0/15 * * * ? *", Locale.ENGLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository, never()).save(any());
        verify(messageSource).getMessage(eq("store.marketplaces.import.schedule.error.missing"), any(), any(Locale.class));
    }

    @Test
    void adminCannotTargetAnotherStoreThroughTheSubmittedStoreId() {
        // given
        Store foreign = new Store();
        foreign.setStoreId("store-2");
        foreign.getMarketplaces().add(new MarketplaceIntegration("Allegro"));
        when(storesRepository.findById("store-2")).thenReturn(foreign);

        // when
        controller.updateMarketplaceOrdersImportSchedule("store-2", "Allegro", "0/15 * * * ? *", Locale.ENGLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        verify(storesRepository, never()).save(foreign);
        verify(ordersImportScheduler).apply("store-1", "Allegro", "0/15 * * * ? *");
    }

    @Test
    void superAdminTargetsTheSubmittedStore() {
        // given
        SecurityContextHolder.clearContext();
        authenticateAs(null, "SUPER_ADMIN");
        Store foreign = new Store();
        foreign.setStoreId("store-2");
        foreign.getMarketplaces().add(new MarketplaceIntegration("Allegro"));
        when(storesRepository.findById("store-2")).thenReturn(foreign);

        // when
        String view = controller.updateMarketplaceOrdersImportSchedule("store-2", "Allegro", "0/15 * * * ? *", Locale.ENGLISH, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/marketplaces");
        verify(storesRepository).save(foreign);
        verify(ordersImportScheduler).apply("store-2", "Allegro", "0/15 * * * ? *");
    }

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
