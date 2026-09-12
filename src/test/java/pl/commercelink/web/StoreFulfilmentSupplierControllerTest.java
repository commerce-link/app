package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.SupplierSelectionForm;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.SupplierConnectionForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreFulfilmentSupplierControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSupplierConnectionService storeSupplierConnectionService;
    @Mock
    private SupplierConnectionViewFactory supplierConnectionViewFactory;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreFulfilmentSupplierController controller;

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private SupplierConnectionForm form() {
        SupplierConnectionForm form = new SupplierConnectionForm();
        form.setSupplierName("Elko");
        form.setMode(ConnectionMode.OWN);
        form.setIncludeInPricing(true);
        form.setIncludeInFulfilment(true);
        form.setConfiguration(Map.of("login", "u"));
        return form;
    }

    private void stubEmptyViews() {
        when(supplierConnectionViewFactory.views(any())).thenReturn(
                new SupplierConnectionViewFactory.SupplierConnectionViews(List.of(), List.of()));
        when(supplierRegistry.getExternalSupplierNames()).thenReturn(List.of());
    }

    @Test
    void theSubmittedFeedScheduleReachesTheService() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.connectOrUpdate(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        SupplierConnectionForm form = form();
        form.setFeedSchedule("0 5,17 * * ? *");

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            controller.save(form, Locale.ENGLISH, new ConcurrentModel(), new MockHttpServletResponse());

            // then
            ArgumentCaptor<SupplierSelectionForm> captor = ArgumentCaptor.forClass(SupplierSelectionForm.class);
            verify(storeSupplierConnectionService).connectOrUpdate(any(), captor.capture(), anyMap());
            assertThat(captor.getValue().getFeedSchedule()).isEqualTo("0 5,17 * * ? *");
        }
    }

    @Test
    void savingASupplierDelegatesToTheServiceAndReturnsTheExternalSectionFragment() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.connectOrUpdate(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of("Elko"), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
            assertThat(view).doesNotContain("(");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("ok");
            verify(storeSupplierConnectionService).connectOrUpdate(any(), any(), eq(Map.of("login", "u")));
        }
    }

    @Test
    void aFailedValidationReturnsTheSmallErrorFragmentWithANon2xxStatus() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.connectOrUpdate(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(
                        List.of(ErrorMessage.of("store.supplier.connection.error.requires.field", "Elko", "Login")),
                        Set.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("missing field");
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
            assertThat(model.getAttribute("errorMessage")).isEqualTo("missing field");
        }
    }

    @Test
    void theSuperAdminVariantRendersTheSectionForTheStoreFromThePath() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.disconnect(any(), eq("Elko")))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of("Elko"), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = controller.disconnectForStore(STORE_ID, "Elko", Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
            assertThat(view).doesNotContain("(");
            verify(storeSupplierConnectionService).disconnect(any(), eq("Elko"));
        }
    }

    @Test
    void theSuperAdminSaveVariantUsesTheStoreFromThePathNotTheSecurityContext() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.connectOrUpdate(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of("Elko"), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            // getStoreId() is intentionally not stubbed: a regression that read the store from
            // the security context instead of the path variable would look up a different store
            // and this test would fail rather than pass silently.

            // when
            String view = controller.saveForStore(STORE_ID, form(), Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
            assertThat(view).doesNotContain("(");
            verify(storeSupplierConnectionService).connectOrUpdate(any(), any(), eq(Map.of("login", "u")));
        }
    }

    @Test
    void theAdminDisconnectVariantDisconnectsTheSupplierNamedInThePath() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.disconnect(any(), eq("Elko")))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of("Elko"), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.disconnect("Elko", Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
            assertThat(view).doesNotContain("(");
            verify(storeSupplierConnectionService).disconnect(any(), eq("Elko"));
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
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(model.getAttribute("errorMessage")).isEqualTo("Store not found.");
        }
    }

    @Test
    void sectionRendersTheExternalSectionWithNoSuccessMessage() {
        // given: used only to refresh the section from elsewhere on the page (the fulfilment
        // settings save, when it flips canUseGlobalSuppliers), so it must not show a toast of its
        // own -- see StoreFulfilmentSettingsController
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.section(Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
            assertThat(view).doesNotContain("(");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.getAttribute("sectionSuccessMessage")).isNull();
        }
    }

    @Test
    void sectionForStoreRendersTheSectionForTheStoreFromThePath() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            // getStoreId() is intentionally not stubbed: a regression that read the store from the
            // security context instead of the path variable would look up a different store and
            // this test would fail rather than pass silently.

            // when
            String view = controller.sectionForStore(STORE_ID, Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
        }
    }

    @Test
    void sectionReturnsTheSmallErrorFragmentWhenTheStoreIsMissing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Store not found.");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = controller.section(Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }
}
