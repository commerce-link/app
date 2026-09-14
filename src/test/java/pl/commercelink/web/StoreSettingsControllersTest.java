package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.ui.Model;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.StoreSettingsOverview;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSettingsControllersTest {

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private StoreSettingsOverviewFactory storeSettingsOverviewFactory;

    @InjectMocks
    private StoreController storeController;

    @InjectMocks
    private SuperAdminController superAdminController;

    private final StoreSettingsOverview overview = new StoreSettingsOverview(List.of(), List.of());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setName("Sklep Demo");
        return store;
    }

    @Test
    void storeAdminSeesTheOverviewOfTheirOwnStore() {
        // given
        CustomUser user = new CustomUser(null, null, Map.of("storeId", "store-1", "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Store store = store("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeSettingsOverviewFactory.build(store, UserRole.ADMIN)).thenReturn(overview);
        Model model = new ExtendedModelMap();

        // when
        String view = storeController.store(model);

        // then
        assertThat(view).isEqualTo("store");
        assertThat(model.getAttribute("overview")).isSameAs(overview);
        assertThat(model.getAttribute("isSuperAdmin")).isEqualTo(false);
    }

    @Test
    void superAdminSeesTheOverviewOfTheStoreTheyOpened() {
        // given
        Store store = store("store-2");
        when(storesRepository.findById("store-2")).thenReturn(store);
        when(storeSettingsOverviewFactory.build(store, UserRole.SUPER_ADMIN)).thenReturn(overview);
        Model model = new ExtendedModelMap();

        // when
        String view = superAdminController.superAdminStore("store-2", model);

        // then
        assertThat(view).isEqualTo("store");
        assertThat(model.getAttribute("overview")).isSameAs(overview);
        assertThat(model.getAttribute("isSuperAdmin")).isEqualTo(true);
    }

    @Test
    void superAdminGetsTheErrorPageWithoutAnOverviewForAnUnknownStore() {
        // given
        Model model = new ExtendedModelMap();

        // when
        String view = superAdminController.superAdminStore("missing", model);

        // then
        assertThat(view).isEqualTo("error");
        verify(storeSettingsOverviewFactory, never()).build(any(), any());
    }
}
