package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PimCategoryOptions.TopLevelChoice;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StoreCategoriesSettingsControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private MessageSource messageSource;

    private StoreCategoriesSettingsController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        controller = new StoreCategoriesSettingsController(storesRepository, new PimCategoryOptions(pimCatalog),
                messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesTickedCategoriesOnTheStoreFromTheSession() {
        // given
        Store store = storeWith("store-1");
        catalogueOffers("Biuro", "Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();

        // when
        String view = controller.saveCategories(submitted("Dom"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).containsExactly("Dom");
        assertThat(view).isEqualTo("redirect:/dashboard/store/categories");
    }

    @Test
    void superAdminSavesOnTheStoreFromThePathAndReturnsToIt() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = storeWith("store-2");
        catalogueOffers("Dom");
        when(storesRepository.findById("store-2")).thenReturn(store);
        successMessage();

        // when
        String view = controller.superAdminSaveCategories("store-2", submitted("Dom"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).containsExactly("Dom");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/categories");
    }

    @Test
    void savesEmptySelectionWhenNothingIsTicked() {
        // given
        Store store = storeWith("store-1", "Dom");
        catalogueOffers("Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();

        // when
        controller.saveCategories(submitted(), null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).isEmpty();
    }

    @Test
    void createsFulfilmentConfigurationWhenTheStoreHasNone() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        catalogueOffers("Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();

        // when
        controller.saveCategories(submitted("Dom"), null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).containsExactly("Dom");
    }

    @Test
    void ignoresASubmittedNameThePageNeverOffered() {
        // given
        Store store = storeWith("store-1");
        catalogueOffers("Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();

        // when
        controller.saveCategories(submitted("Dom", "Wymyślona"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(store.getEnabledCategories()).containsExactly("Dom");
    }

    @Test
    void keepsASavedCategoryTheCatalogueNoLongerOffers() {
        // given
        Store store = storeWith("store-1", "Zniknięta");
        catalogueOffers("Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();

        // when
        controller.saveCategories(submitted("Zniknięta"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(store.getEnabledCategories()).containsExactly("Zniknięta");
    }

    @Test
    void rendersChoicesWithTheCurrentSelectionAndItsCount() {
        // given
        Store store = storeWith("store-1", "Dom");
        catalogueOffers("Biuro", "Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.categories(model);

        // then
        assertThat(view).isEqualTo("store-categories");
        assertThat(model.getAttribute("choices")).isEqualTo(List.of(
                new TopLevelChoice("Biuro", false, true),
                new TopLevelChoice("Dom", true, true)));
        assertThat(model.getAttribute("selectedCount")).isEqualTo(1L);
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/categories");
    }

    @Test
    void savingWithoutReloadingAnswersWithTheFormFragmentAndTheSuccessMessage() {
        // given
        Store store = storeWith("store-1");
        catalogueOffers("Dom");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.saveCategories(submitted("Dom"), "fetch", model, PL, redirectAttributes, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-categories :: categoriesForm");
        assertThat(model.getAttribute("savedMessage")).isEqualTo("zapisano");
        assertThat(model.getAttribute("choices")).isEqualTo(List.of(new TopLevelChoice("Dom", true, true)));
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE);
    }

    @Test
    void anUnknownStoreIsNotFound() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        when(storesRepository.findById("missing")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.superAdminCategories("missing", new ExtendedModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> controller.superAdminSaveCategories("missing", submitted("Dom"), null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
        verify(storesRepository, never()).save(any(Store.class));
    }

    @Test
    void aSingleTickedCategoryWithACommaInItsNameIsSavedWhole() throws Exception {
        // given
        Store store = storeWith("store-1");
        catalogueOffers("Dom", "Żywność, napoje i tytoń");
        when(storesRepository.findById("store-1")).thenReturn(store);
        successMessage();

        // when
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/dashboard/store/categories").param("enabledCategories", "Żywność, napoje i tytoń"))
                .andExpect(status().is3xxRedirection());

        // then
        assertThat(store.getEnabledCategories()).containsExactly("Żywność, napoje i tytoń");
    }

    @Test
    void anEmptyCatalogueShowsTheListAsUnavailableInsteadOfEverySavedCategoryAsMissing() {
        // given
        Store store = storeWith("store-1", "Dom");
        when(pimCatalog.allCategories()).thenReturn(List.of());
        when(storesRepository.findById("store-1")).thenReturn(store);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.categories(model);

        // then
        assertThat(view).isEqualTo("store-categories");
        assertThat(model.getAttribute("catalogueAvailable")).isEqualTo(false);
    }

    @Test
    void anEmptyCatalogueRejectsTheSaveAndKeepsTheSavedCategories() {
        // given
        Store store = storeWith("store-1", "Dom");
        when(pimCatalog.allCategories()).thenReturn(List.of());
        when(storesRepository.findById("store-1")).thenReturn(store);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.saveCategories(submitted(), "fetch", new ExtendedModelMap(), PL, new RedirectAttributesModelMap(),
                response);

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(store.getEnabledCategories()).containsExactly("Dom");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    private static MockHttpServletRequest submitted(String... names) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (names.length > 0) {
            request.addParameter("enabledCategories", names);
        }
        return request;
    }

    private void catalogueOffers(String... names) {
        when(pimCatalog.allCategories()).thenReturn(List.of(names).stream()
                .map(name -> new PimCategory(name, null, name, "pl"))
                .toList());
    }

    private void successMessage() {
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("zapisano");
    }

    private Store storeWith(String storeId, String... categories) {
        Store store = new Store();
        store.setStoreId(storeId);
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledCategories(List.of(categories));
        store.setFulfilmentConfiguration(configuration);
        return store;
    }

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))
        );
    }
}
