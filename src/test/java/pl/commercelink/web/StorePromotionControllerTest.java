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
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.PromotionsRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.PromotionDetailsView;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePromotionControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PROMOTION_ID = "6f1c2a9e-3b4d-4c5e-8f70-1a2b3c4d5e6f";
    private static final String GONE_ID = "00000000-0000-0000-0000-000000000000";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PromotionsRepository promotionsRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StorePromotionController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theDetailsShowThePromotionOfTheSessionStore() {
        // given
        store("store-1");
        promotion("store-1", true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.details(PROMOTION_ID, model, PL);

        // then
        assertThat(view).isEqualTo("store-promotion");
        PromotionDetailsView details = (PromotionDetailsView) model.getAttribute("details");
        assertThat(details.summary().name()).isEqualTo("Tydzień marki ASUS");
        assertThat(details.toggleHref()).isEqualTo("/dashboard/store/promotions/" + PROMOTION_ID + "/toggle");
        assertThat(details.deleteHref()).isEqualTo("/dashboard/store/promotions/" + PROMOTION_ID + "/delete");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/promotions");
        assertThat(model.getAttribute("backLabel")).isEqualTo("store.promotions");
    }

    @Test
    void theSuperAdminSeesThePromotionOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        promotion("store-2", true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminDetails("store-2", PROMOTION_ID, model, PL);

        // then
        PromotionDetailsView details = (PromotionDetailsView) model.getAttribute("details");
        assertThat(details.toggleHref()).isEqualTo("/dashboard/store/store-2/promotions/" + PROMOTION_ID + "/toggle");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/store-2/promotions");
    }

    @Test
    void anUnknownStoreOrPromotionAnswers404OnEveryAction() {
        // given
        store("store-1");
        when(promotionsRepository.find("store-1", GONE_ID)).thenReturn(Optional.empty());
        List<Consumer<String>> calls = List.of(
                id -> controller.details(id, new ExtendedModelMap(), PL),
                id -> controller.toggle(id, false, PL, new RedirectAttributesModelMap()),
                id -> controller.confirmDelete(id, new ExtendedModelMap(), PL));

        // when / then
        calls.forEach(call -> assertThatThrownBy(() -> call.accept(GONE_ID)).isInstanceOf(ResponseStatusException.class));
        authenticateAs(null, "SUPER_ADMIN");
        assertThatThrownBy(() -> controller.superAdminDetails("missing", PROMOTION_ID, new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        verify(promotionsRepository, never()).setActive(anyString(), anyString(), anyBoolean());
    }

    @Test
    void togglingAnActivePromotionSwitchesItOff() {
        // given
        store("store-1");
        promotion("store-1", true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.toggle(PROMOTION_ID, false, PL, redirectAttributes);

        // then
        verify(promotionsRepository).setActive("store-1", PROMOTION_ID, false);
        assertThat(view).isEqualTo("redirect:/dashboard/store/promotions/" + PROMOTION_ID);
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.promotions.toggled.off");
    }

    @Test
    void togglingASwitchedOffPromotionSwitchesItOn() {
        // given
        store("store-1");
        promotion("store-1", false);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.toggle(PROMOTION_ID, true, PL, redirectAttributes);

        // then
        verify(promotionsRepository).setActive("store-1", PROMOTION_ID, true);
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.promotions.toggled.on");
    }

    @Test
    void theSuperAdminTogglesThePromotionOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        promotion("store-2", true);

        // when
        String view = controller.superAdminToggle("store-2", PROMOTION_ID, false, PL, new RedirectAttributesModelMap());

        // then
        verify(promotionsRepository).setActive("store-2", PROMOTION_ID, false);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/promotions/" + PROMOTION_ID);
    }

    @Test
    void theConfirmationPageDeletesNothing() {
        // given
        store("store-1");
        promotion("store-1", true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmDelete(PROMOTION_ID, model, PL);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/store/promotions/" + PROMOTION_ID + "/delete");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/store/promotions/" + PROMOTION_ID);
        assertThat(confirm.destructive()).isTrue();
        verify(promotionsRepository, never()).delete(any(Promotion.class));
    }

    @Test
    void deletingAPromotionReturnsToTheListWithAMessage() {
        // given
        store("store-1");
        Promotion promotion = promotion("store-1", true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.delete(PROMOTION_ID, PL, redirectAttributes);

        // then
        verify(promotionsRepository).delete(promotion);
        assertThat(view).isEqualTo("redirect:/dashboard/store/promotions");
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.promotions.deleted");
    }

    @Test
    void deletingAPromotionThatIsAlreadyGoneReturnsToTheList() {
        // given
        store("store-1");
        when(promotionsRepository.find("store-1", GONE_ID)).thenReturn(Optional.empty());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.delete(GONE_ID, PL, redirectAttributes);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/promotions");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE);
        verify(promotionsRepository, never()).delete(any(Promotion.class));
    }

    @Test
    void theSuperAdminDeletesInTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        Promotion promotion = promotion("store-2", true);

        // when
        String view = controller.superAdminDelete("store-2", PROMOTION_ID, PL, new RedirectAttributesModelMap());

        // then
        verify(promotionsRepository).delete(promotion);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/promotions");
    }

    private Promotion promotion(String storeId, boolean active) {
        Promotion promotion = new Promotion();
        promotion.setStoreId(storeId);
        promotion.setPromotionId(PROMOTION_ID);
        promotion.setName("Tydzień marki ASUS");
        promotion.setType(DiscountType.Percentage);
        promotion.setValue(10);
        promotion.setActive(active);
        when(promotionsRepository.find(storeId, PROMOTION_ID)).thenReturn(Optional.of(promotion));
        return promotion;
    }

    private void store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
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
