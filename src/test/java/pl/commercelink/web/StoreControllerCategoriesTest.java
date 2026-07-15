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
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreControllerCategoriesTest {

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private PimCategoryOptions pimCategoryOptions;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresSubmittedStoreIdAndSavesCategoriesOnTheStoreOfTheLoggedInAdmin() {
        // given
        Store ownStore = storeWithCategories("store-1");
        Store foreignStore = storeWithCategories("store-2");
        when(storesRepository.findById("store-1")).thenReturn(ownStore);
        when(storesRepository.findById("store-2")).thenReturn(foreignStore);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("ok");

        // when
        controller.updateStoreCategories("store-2", List.of("Dom"), Locale.forLanguageTag("pl"), new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(ownStore);
        verify(storesRepository, never()).save(foreignStore);
        assertThat(ownStore.getEnabledCategories()).containsExactly("Dom");
        assertThat(foreignStore.getEnabledCategories()).isEmpty();
    }

    @Test
    void superAdminSavesCategoriesOnTheSubmittedStore() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store foreignStore = storeWithCategories("store-2");
        when(storesRepository.findById("store-2")).thenReturn(foreignStore);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("ok");

        // when
        controller.updateStoreCategories("store-2", List.of("Dom"), Locale.forLanguageTag("pl"), new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(foreignStore);
        assertThat(foreignStore.getEnabledCategories()).containsExactly("Dom");
    }

    private Store storeWithCategories(String storeId, String... categories) {
        Store store = new Store();
        store.setStoreId(storeId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledCategories(List.of(categories));
        store.setFulfilmentConfiguration(config);
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

    @Test
    void rendersCategorySettingsWithTopLevelsAndCurrentSelection() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledCategories(List.of("Dom"));
        store.setFulfilmentConfiguration(config);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(pimCategoryOptions.topLevelNames()).thenReturn(List.of("Biuro", "Dom"));
        Model model = new ExtendedModelMap();

        // when
        String view = controller.superAdminStoreCategories("store-1", model);

        // then
        assertThat(view).isEqualTo("store-categories");
        assertThat(model.getAttribute("categoryNames")).isEqualTo(List.of("Biuro", "Dom"));
        assertThat(model.getAttribute("enabledCategories")).isEqualTo(List.of("Dom"));
    }

    @Test
    void savesSelectedCategoriesOnStore() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("ok");

        // when
        controller.updateStoreCategories("store-1", List.of("Dom", "Biuro"), Locale.forLanguageTag("pl"), new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).containsExactly("Dom", "Biuro");
    }

    @Test
    void savesEmptySelectionWhenNoCategoriesChecked() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setEnabledCategories(List.of("Dom"));
        store.setFulfilmentConfiguration(config);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("ok");

        // when
        controller.updateStoreCategories("store-1", null, Locale.forLanguageTag("pl"), new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).isEmpty();
    }

    @Test
    void savesCategoriesWhenStoreHasNoFulfilmentConfigurationYet() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("ok");

        // when
        controller.updateStoreCategories("store-1", List.of("Dom"), Locale.forLanguageTag("pl"), new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getEnabledCategories()).containsExactly("Dom");
    }
}
