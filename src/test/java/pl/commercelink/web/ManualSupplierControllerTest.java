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
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualSupplierControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String IDENTITY = "manual:Hurtownia X";

    @Mock
    private ManualSupplierService manualSupplierService;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierConnectionViewFactory supplierConnectionViewFactory;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ManualSupplierController controller;

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private void stubEmptyViews() {
        when(supplierConnectionViewFactory.views(any())).thenReturn(
                new SupplierConnectionViewFactory.SupplierConnectionViews(List.of(), List.of()));
    }

    @Test
    void savingOneManualSupplierPassesASingleSelectionAndReturnsTheManualSectionFragment() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.saveSelection(IDENTITY, true, true, false, Locale.ENGLISH, model, response);

            // then
            assertThat(view).startsWith("fragments/supplier-section :: supplierSection(");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("ok");
            verify(manualSupplierService).applySelections(eq(STORE_ID),
                    eq(List.of(new ManualSupplierService.ManualSelection(IDENTITY, true, true, false))));
        }
    }

    @Test
    void theSuperAdminSaveVariantRendersTheSectionForTheStoreFromThePath() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = controller.saveSelectionForStore(STORE_ID, IDENTITY, false, true, true,
                    Locale.ENGLISH, model, response);

            // then
            assertThat(view).startsWith("fragments/supplier-section :: supplierSection(");
            assertThat(model.getAttribute("sectionBasePath")).isEqualTo("/dashboard/store/" + STORE_ID);
            verify(manualSupplierService).applySelections(eq(STORE_ID),
                    eq(List.of(new ManualSupplierService.ManualSelection(IDENTITY, false, true, true))));
        }
    }

    @Test
    void aMissingStoreOnSaveSelectionReturnsTheSmallErrorFragmentInsteadOfThrowing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Store not found.");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.saveSelection(IDENTITY, true, true, true, Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void deletingAManualSupplierReturnsTheManualSectionFragmentOnSuccess() {
        // given
        when(manualSupplierService.delete(STORE_ID, IDENTITY)).thenReturn(ManualSupplierService.Result.success());
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("deleted");
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.delete(IDENTITY, Locale.ENGLISH, model, response);

            // then
            assertThat(view).startsWith("fragments/supplier-section :: supplierSection(");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("deleted");
        }
    }

    @Test
    void deletingAManualSupplierReturnsTheSmallErrorFragmentOnFailure() {
        // given
        when(manualSupplierService.delete(STORE_ID, IDENTITY))
                .thenReturn(ManualSupplierService.Result.error("store.manual.error.supplier.notfound"));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("not found");
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.delete(IDENTITY, Locale.ENGLISH, model, response);

            // then
            assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(model.getAttribute("errorMessage")).isEqualTo("not found");
        }
    }

    @Test
    void theSectionEndpointRendersTheManualSectionForRefreshingAfterCreateOrUpload() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.section(model);

            // then
            assertThat(view).startsWith("fragments/supplier-section :: supplierSection(");
            assertThat(model.getAttribute("sectionSuccessMessage")).isNull();
        }
    }

    @Test
    void theSuperAdminSectionVariantRendersForTheStoreFromThePath() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        stubEmptyViews();
        ConcurrentModel model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = controller.sectionForStore(STORE_ID, model);

            // then
            assertThat(view).startsWith("fragments/supplier-section :: supplierSection(");
            assertThat(model.getAttribute("sectionBasePath")).isEqualTo("/dashboard/store/" + STORE_ID);
        }
    }
}
