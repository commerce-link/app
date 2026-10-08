package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.stores.Store;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreAllegroShippingControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock private StoresRepository storesRepository;
    @Mock private AllegroShippingSettings allegroShippingSettings;

    private StoreAllegroShippingController controller;
    private Store store;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUser(null, null, Map.of("role", "SUPER_ADMIN")), null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(allegroShippingSettings.installed()).thenReturn(true);
        when(allegroShippingSettings.labelFormat(store)).thenReturn(AllegroShippingLabelFormat.PDF_A6);
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        controller = new StoreAllegroShippingController(storesRepository, allegroShippingSettings, messages);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thePageShowsWhatAllegroSaysAboutTheConnection() {
        // given
        when(allegroShippingSettings.status(store)).thenReturn(AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.superAdminPage("store-1", model, POLISH);

        // then
        assertThat(view).isEqualTo("store-shipping-allegro");
        assertThat(model.get("status")).isEqualTo(AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT);
        assertThat(model.get("reconnectHref")).isEqualTo("/dashboard/store/store-1/marketplaces/Allegro/authorize");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/store-1/shipping/allegro");
    }

    @Test
    void withoutTheAdapterThePageDoesNotExist() {
        // given
        when(allegroShippingSettings.installed()).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> controller.superAdminPage("store-1", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void enablingSavesTheStoreAndGoesBackToShipping() {
        // given
        when(allegroShippingSettings.status(store)).thenReturn(AllegroShippingStatus.READY);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.superAdminSave("store-1", "ZPL", new ExtendedModelMap(), POLISH, redirect);

        // then
        verify(allegroShippingSettings).enable(store, AllegroShippingLabelFormat.ZPL);
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/shipping");
    }

    @Test
    void aRefusedEnableShowsThePageAgainWithoutSaving() {
        // given
        when(allegroShippingSettings.status(store)).thenReturn(AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT);
        doThrow(new IllegalStateException("MISSING_SHIPMENTS_CONSENT"))
                .when(allegroShippingSettings).enable(any(), any());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.superAdminSave("store-1", "PDF_A6", model, POLISH, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("store-shipping-allegro");
        assertThat(model.get("errorMessage")).isEqualTo("shipping.allegro.enable.refused");
        verify(storesRepository, never()).save(any(Store.class));
    }

    @Test
    void disconnectingSwitchesItOffAndSavesTheStore() {
        // given
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.superAdminDisconnect("store-1", POLISH, redirect);

        // then
        verify(allegroShippingSettings).disable(store);
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/shipping");
    }
}
