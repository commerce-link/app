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
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DiscountCodeForm;
import pl.commercelink.web.dtos.PaymentOptionView;
import pl.commercelink.web.settings.DiscountCategoryGroup;
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
class StoreNewDiscountCodeControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DiscountCodesRepository discountCodesRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private PaymentGateways paymentGateways;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreNewDiscountCodeController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentGateways.paymentOptions(any())).thenReturn(List.of(new PaymentOptionView("paynow", "PayNow", true)));
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theNewCodePageOffersAnEmptyFormForTheSessionStore() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newCode(model, PL);

        // then
        assertThat(view).isEqualTo("store-discount-code-new");
        assertThat(((DiscountCodeForm) model.getAttribute("form")).getType()).isEqualTo("Percentage");
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/discounts/new");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/discounts");
        assertThat(model.getAttribute("backLabel")).isEqualTo("store.discounts");
        assertThat(model.getAttribute("paymentOptions")).asList().extracting("name").containsExactly("paynow");
    }

    @Test
    void theSuperAdminGetsTheFormOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminNewCode("store-2", model, PL);

        // then
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/store-2/discounts/new");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/store-2/discounts");
    }

    @Test
    void anUnknownStoreAnswers404() {
        // given
        authenticateAs(null, "SUPER_ADMIN");

        // when / then
        assertThatThrownBy(() -> controller.superAdminNewCode("missing", new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.superAdminCreate("missing", validForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
        verify(discountCodesRepository, never()).create(any());
    }

    @Test
    void categoriesComeGroupedByCatalogWithEachNameOnce() {
        // given
        store("store-1");
        ProductCatalog parts = catalog("Podzespoły", "Karty graficzne", "Procesory");
        ProductCatalog outlet = catalog("Outlet", "Karty graficzne", "Laptopy poleasingowe");
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(parts, outlet));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.newCode(model, PL);

        // then
        @SuppressWarnings("unchecked")
        List<DiscountCategoryGroup> groups = (List<DiscountCategoryGroup>) model.getAttribute("categoryGroups");
        assertThat(groups).extracting(DiscountCategoryGroup::catalogName).containsExactly("Podzespoły", "Outlet");
        assertThat(groups.get(0).categories()).extracting(DiscountCategoryGroup.Choice::name)
                .containsExactly("Karty graficzne", "Procesory");
        assertThat(groups.get(0).categories().get(0).alsoIn()).containsExactly("Outlet");
        assertThat(groups.get(1).categories()).extracting(DiscountCategoryGroup.Choice::name)
                .containsExactly("Laptopy poleasingowe");
    }

    @Test
    void anInvalidCodeSentWithoutReloadingAnswers422WithTheFormAlone() {
        // given
        store("store-1");
        DiscountCodeForm form = validForm();
        form.setCode("lato_10");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.create(form, "fetch", model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        assertThat(view).isEqualTo("store-discount-code-new :: codeForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("code", "store.discounts.code.invalid"));
        verify(discountCodesRepository, never()).create(any());
    }

    @Test
    void anInvalidCodeSentWithoutJavaScriptRendersTheWholePage() {
        // given
        store("store-1");
        DiscountCodeForm form = validForm();
        form.setName("");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.create(form, null, model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-discount-code-new");
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("name", "store.discounts.name.required"));
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/discounts");
    }

    @Test
    void aValidCodeIsCreatedForTheSessionStoreAndTheListSaysSo() {
        // given
        store("store-1");
        when(discountCodesRepository.create(any())).thenReturn(true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.create(validForm(), null, new ExtendedModelMap(), PL, redirectAttributes,
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<DiscountCode> created = ArgumentCaptor.forClass(DiscountCode.class);
        verify(discountCodesRepository).create(created.capture());
        assertThat(created.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(created.getValue().getCode()).isEqualTo("LATO-10");
        assertThat(view).isEqualTo("redirect:/dashboard/store/discounts");
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.discounts.created");
    }

    @Test
    void aValidCodeSentWithoutReloadingLeavesTheMessageForTheListAndNavigatesThere() {
        // given
        store("store-1");
        when(discountCodesRepository.create(any())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.create(validForm(), "fetch", model, PL, new RedirectAttributesModelMap(), request, response);

        // then
        assertThat(view).isEqualTo("store-discount-code-new :: codeForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.getAttribute("redirectTo")).isEqualTo("/dashboard/store/discounts");
        Map<String, Object> flash = RequestContextUtils.getOutputFlashMap(request);
        assertThat(flash).containsEntry(SettingsFlash.SAVED_MESSAGE, "store.discounts.created");
    }

    @Test
    void aCodeTheStoreAlreadyHasIsAnErrorAtTheCodeField() {
        // given
        store("store-1");
        when(discountCodesRepository.create(any())).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.create(validForm(), "fetch", model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        assertThat(view).isEqualTo("store-discount-code-new :: codeForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("code", "store.discounts.code.taken"));
    }

    @Test
    void theSuperAdminCreatesInTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        when(discountCodesRepository.create(any())).thenReturn(true);

        // when
        String view = controller.superAdminCreate("store-2", validForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<DiscountCode> created = ArgumentCaptor.forClass(DiscountCode.class);
        verify(discountCodesRepository).create(created.capture());
        assertThat(created.getValue().getStoreId()).isEqualTo("store-2");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/discounts");
    }

    private static DiscountCodeForm validForm() {
        DiscountCodeForm form = DiscountCodeForm.empty();
        form.setCode("lato-10");
        form.setName("Kampania letnia");
        form.setValue("10");
        return form;
    }

    private static ProductCatalog catalog(String name, String... categories) {
        ProductCatalog catalog = new ProductCatalog("store-1", name);
        for (String category : categories) {
            CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
            definition.setName(category);
            catalog.getCategories().add(definition);
        }
        return catalog;
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.addPaymentIntegration("paynow");
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
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
