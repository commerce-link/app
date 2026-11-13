package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.PromotionsRepository;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PromotionForm;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreNewPromotionControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PromotionsRepository promotionsRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreNewPromotionController controller;

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
    void theNewPromotionPageOffersAnEmptyFormWithTheDialogsOfTheCodes() {
        // given
        store("store-1");
        ProductCatalog parts = new ProductCatalog("store-1", "Podzespoły");
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(parts));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newPromotion(model, PL);

        // then
        assertThat(view).isEqualTo("store-promotion-new");
        assertThat(((PromotionForm) model.getAttribute("form")).getType()).isEqualTo("Percentage");
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of());
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/promotions/new");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/promotions");
        assertThat(model.getAttribute("backLabel")).isEqualTo("store.promotions");
        assertThat(model.getAttribute("catalogs")).asList().containsExactly(parts);
        assertThat(model.getAttribute("pricelistUrl")).isEqualTo("/dashboard/store/discounts/pricelist");
    }

    @Test
    void theSuperAdminGetsTheFormOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminNewPromotion("store-2", model, PL);

        // then
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/store-2/promotions/new");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/store-2/promotions");
        assertThat(model.getAttribute("pricelistUrl")).isEqualTo("/dashboard/store/store-2/discounts/pricelist");
    }

    @Test
    void anUnknownStoreAnswers404() {
        // given
        authenticateAs(null, "SUPER_ADMIN");

        // when / then
        assertThatThrownBy(() -> controller.superAdminNewPromotion("missing", new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.superAdminCreate("missing", validForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
        verify(promotionsRepository, never()).save(any());
    }

    @Test
    void anInvalidPromotionSentWithoutReloadingAnswers422WithTheFormAlone() {
        // given
        store("store-1");
        PromotionForm form = validForm();
        form.setName("");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.create(form, "fetch", model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        assertThat(view).isEqualTo("store-promotion-new :: promotionForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("name", "store.promotions.name.required"));
        verify(promotionsRepository, never()).save(any());
    }

    @Test
    void anInvalidPromotionSentWithoutJavaScriptRendersTheWholePage() {
        // given
        store("store-1");
        PromotionForm form = validForm();
        form.setValue("");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.create(form, null, model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-promotion-new");
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("value", "store.discounts.value.required"));
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/promotions");
    }

    @Test
    void aValidPromotionIsSavedForTheSessionStoreAndTheListSaysSo() {
        // given
        store("store-1");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.create(validForm(), null, new ExtendedModelMap(), PL, redirectAttributes,
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionsRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(saved.getValue().getName()).isEqualTo("Tydzień marki ASUS");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(view).isEqualTo("redirect:/dashboard/store/promotions");
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.promotions.created");
    }

    @Test
    void aValidPromotionSentWithoutReloadingLeavesTheMessageForTheListAndNavigatesThere() {
        // given
        store("store-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.create(validForm(), "fetch", model, PL, new RedirectAttributesModelMap(), request, response);

        // then
        assertThat(view).isEqualTo("store-promotion-new :: promotionForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.getAttribute("redirectTo")).isEqualTo("/dashboard/store/promotions");
        Map<String, Object> flash = RequestContextUtils.getOutputFlashMap(request);
        assertThat(flash).containsEntry(SettingsFlash.SAVED_MESSAGE, "store.promotions.created");
    }

    @Test
    void theSuperAdminCreatesInTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");

        // when
        String view = controller.superAdminCreate("store-2", validForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionsRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-2");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/promotions");
    }

    private static PromotionForm validForm() {
        PromotionForm form = PromotionForm.empty();
        form.setName("Tydzień marki ASUS");
        form.setValue("10");
        return form;
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
