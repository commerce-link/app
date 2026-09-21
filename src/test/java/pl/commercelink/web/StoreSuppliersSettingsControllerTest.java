package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.SupplierAdminSettingsForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSuppliersSettingsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierConnections suppliers;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreSuppliersSettingsController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(true);
        config.setInventoryCacheTtlMinutes(30);
        store.setFulfilmentConfiguration(config);
        when(storesRepository.findById(storeId)).thenReturn(store);
        when(suppliers.views(any(), any())).thenReturn(List.of());
        return store;
    }

    @Test
    void aStoreAdminSeesTheListWithoutTheApplicationAdminSettings() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.suppliers(model, POLISH);

        // then
        assertThat(view).isEqualTo("store-suppliers");
        assertThat(model.get("adminForm")).isNull();
        assertThat(model.get("newSupplierHref")).isEqualTo("/dashboard/store/suppliers/new");
        verify(suppliers).views(any(), eq("/dashboard/store/suppliers"));
    }

    @Test
    void theApplicationAdminSeesTheStoresSupplierSettings() {
        // given
        logInAs("SUPER_ADMIN", "none");
        store("store-9");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminSuppliers("store-9", model, POLISH);

        // then
        SupplierAdminSettingsForm form = (SupplierAdminSettingsForm) model.get("adminForm");
        assertThat(form.isCanUseGlobalSuppliers()).isTrue();
        assertThat(form.getInventoryCacheTtlMinutes()).isEqualTo("30");
        assertThat(model.get("adminFormAction")).isEqualTo("/dashboard/store/store-9/suppliers");
    }

    @Test
    void theApplicationAdminSavesTheStoreFromThePath() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9");
        when(suppliers.saveAdminSettings(store, false, null)).thenReturn(true);
        when(messageSource.getMessage(eq("store.suppliers.admin.saved"), any(), eq(POLISH))).thenReturn("Zapisano");
        SupplierAdminSettingsForm form = new SupplierAdminSettingsForm();
        form.setInventoryCacheTtlMinutes(" ");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.saveAdminSettings("store-9", form, null, new ExtendedModelMap(), POLISH, redirect,
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(suppliers).saveAdminSettings(store, false, null);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/suppliers");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zapisano");
    }

    @Test
    void aMistypedCacheTimeIsNotSaved() {
        // given
        logInAs("SUPER_ADMIN", "none");
        store("store-9");
        SupplierAdminSettingsForm form = new SupplierAdminSettingsForm();
        form.setInventoryCacheTtlMinutes("pół godziny");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveAdminSettings("store-9", form, "fetch", model, POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        verify(suppliers, never()).saveAdminSettings(any(), anyBoolean(), any());
        assertThat(view).isEqualTo("store-suppliers :: adminForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.get("errors")).isEqualTo(Map.of("inventoryCacheTtlMinutes", "store.suppliers.admin.cacheTtl.invalid"));
    }
}
