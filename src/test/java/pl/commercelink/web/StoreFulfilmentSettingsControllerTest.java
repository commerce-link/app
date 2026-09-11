package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreFulfilmentSettingsControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSupplierConnectionService storeSupplierConnectionService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreFulfilmentSettingsController controller;

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(false);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private FulfilmentSettingsForm form() {
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.setOrderAssemblyDays(1);
        form.setOrderRealizationDays(2);
        form.setAutomatedFulfilment(true);
        form.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);
        form.setCanUseGlobalSuppliers(true);
        form.setInventoryCacheTtlMinutes(30);
        return form;
    }

    @Test
    void savingSettingsOnlyAppliesStoreSettingsAndNeverTouchesConnections() {
        // given: connections have their own per-supplier endpoints; this one must never call
        // connectOrUpdate/disconnect, only applyStoreSettings
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, model, response);

            // then -- a no-argument view name: ThymeleafView rejects positional fragment parameters
            assertThat(view).isEqualTo("fragments/fulfilment-settings-section :: section");
            assertThat(view).doesNotContain("(");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("ok");
            verify(storeSupplierConnectionService).applyStoreSettings(any(), any(), eq(false));
            verify(storeSupplierConnectionService, never()).connectOrUpdate(any(), any(), any());
            verify(storeSupplierConnectionService, never()).disconnect(any(), any());
        }
    }

    @Test
    void doesNotFlagAnExternalSupplierRefreshWhenCanUseGlobalSuppliersDidNotChange() {
        // given: the store already has it enabled, and applyStoreSettings leaves it enabled --
        // resolveCanUseGlobalSuppliers is stubbed out here (it is proven separately in
        // StoreSupplierConnectionServiceTest), so this test simulates its effect directly by
        // having the mock leave the store's flag untouched
        Store store = store();
        store.getFulfilmentConfiguration().setCanUseGlobalSuppliers(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            controller.save(form(), Locale.ENGLISH, model, response);

            // then -- must not ask the page to refresh the suppliers section for an unrelated save
            assertThat(model.getAttribute("sectionRefreshExternalSuppliers")).isEqualTo(false);
        }
    }

    @Test
    void flagsAnExternalSupplierRefreshWhenCanUseGlobalSuppliersChanged() {
        // given: applyStoreSettings mutates the same Store instance in place (via
        // StoreSupplierConnectionPersister.saveStore), so this simulates that by flipping the
        // flag on the store as a side effect of the stubbed call
        Store store = store();
        store.getFulfilmentConfiguration().setCanUseGlobalSuppliers(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    store.getFulfilmentConfiguration().setCanUseGlobalSuppliers(true);
                    return new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of());
                });
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            controller.save(form(), Locale.ENGLISH, model, response);

            // then
            assertThat(model.getAttribute("sectionRefreshExternalSuppliers")).isEqualTo(true);
        }
    }

    @Test
    void aFailedSaveReturnsTheSmallErrorFragmentWithANon2xxStatus() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(
                        List.of(ErrorMessage.of("store.supplier.connection.error.update.failed")), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Update failed.");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(model.getAttribute("errorMessage")).isEqualTo("Update failed.");
        }
    }

    @Test
    void theSuperAdminVariantUsesTheStoreFromThePathNotTheSecurityContext() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            // getStoreId() is intentionally not stubbed: a regression that read the store from the
            // security context instead of the path variable would look up a different store and
            // this test would fail rather than pass silently.

            // when
            String view = controller.saveForStore(STORE_ID, form(), Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/fulfilment-settings-section :: section");
            assertThat(view).doesNotContain("(");
            verify(storeSupplierConnectionService).applyStoreSettings(any(), any(), eq(true));
        }
    }

    @Test
    void aMissingStoreReturnsTheSmallErrorFragmentInsteadOfThrowing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Store not found.");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = controller.save(form(), Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(model.getAttribute("errorMessage")).isEqualTo("Store not found.");
        }
    }
}
