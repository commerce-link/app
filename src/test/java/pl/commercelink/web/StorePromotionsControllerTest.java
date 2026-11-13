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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.PromotionsRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.PromotionListView;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePromotionsControllerTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PromotionsRepository promotionsRepository;

    @InjectMocks
    private StorePromotionsController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theListShowsThePromotionsOfTheSessionStore() {
        // given
        store("store-1");
        Promotion promotion = promotion("Tydzień marki ASUS");
        when(promotionsRepository.findAll("store-1")).thenReturn(List.of(promotion));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.promotions(null, null, model);

        // then
        assertThat(view).isEqualTo("store-promotions");
        PromotionListView promotions = (PromotionListView) model.getAttribute("promotions");
        assertThat(promotions.rows()).extracting("name").containsExactly("Tydzień marki ASUS");
        assertThat(promotions.rows().get(0).href()).isEqualTo("/dashboard/store/promotions/" + promotion.getPromotionId());
        assertThat(model.getAttribute("listPath")).isEqualTo("/dashboard/store/promotions");
        assertThat(model.getAttribute("newPromotionHref")).isEqualTo("/dashboard/store/promotions/new");
    }

    @Test
    void theSuperAdminSeesThePromotionsOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminPromotions("store-2", null, null, model);

        // then
        verify(promotionsRepository).findAll("store-2");
        assertThat(model.getAttribute("listPath")).isEqualTo("/dashboard/store/store-2/promotions");
        assertThat(model.getAttribute("newPromotionHref")).isEqualTo("/dashboard/store/store-2/promotions/new");
    }

    @Test
    void anUnknownStoreAnswers404() {
        // given
        authenticateAs(null, "SUPER_ADMIN");

        // when / then
        assertThatThrownBy(() -> controller.superAdminPromotions("missing", null, null, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        verify(promotionsRepository, never()).findAll(anyString());
    }

    private static Promotion promotion(String name) {
        Promotion promotion = new Promotion();
        promotion.setStoreId("store-1");
        promotion.setPromotionId(UUID.randomUUID().toString());
        promotion.setName(name);
        promotion.setType(DiscountType.Percentage);
        promotion.setValue(10);
        promotion.setActive(true);
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
