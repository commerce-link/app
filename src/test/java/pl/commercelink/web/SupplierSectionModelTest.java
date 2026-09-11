package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SupplierSectionModelTest {

    private static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(true);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void basePathUsesTheStorePathOnlyForSuperAdmin() {
        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String path = SupplierSectionModel.basePath("store-1");

            // then
            assertThat(path).isEqualTo("/dashboard/store/store-1");
        }
    }

    @Test
    void basePathIsTheStoreScopedRootForAnOrdinaryAdmin() {
        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String path = SupplierSectionModel.basePath("store-1");

            // then
            assertThat(path).isEqualTo("/dashboard/store");
        }
    }

    @Test
    void renderExternalSectionReturnsTheSupplierSectionFragmentWithTheExternalRowsAndAvailableSuppliers() {
        // given
        SupplierConnectionViewFactory viewFactory = mock(SupplierConnectionViewFactory.class);
        SupplierRegistry registry = mock(SupplierRegistry.class);
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, true);
        when(viewFactory.views(any())).thenReturn(
                new SupplierConnectionViewFactory.SupplierConnectionViews(List.of(elko), List.of()));
        when(registry.getExternalSupplierNames()).thenReturn(List.of("Elko", "Acme"));
        ConcurrentModel model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = SupplierSectionModel.renderExternalSection(viewFactory, registry, store(), "Saved.", model);

            // then
            assertThat(view).startsWith("fragments/supplier-section :: supplierSection(");
            assertThat(model.getAttribute("sectionRows")).isEqualTo(List.of(elko));
            assertThat(model.getAttribute("sectionShowMode")).isEqualTo(true);
            assertThat(model.getAttribute("sectionBasePath")).isEqualTo("/dashboard/store");
            // Elko is already connected, so only Acme is left to offer in the Add dropdown
            assertThat(model.getAttribute("sectionAvailableSuppliers")).isEqualTo(List.of("Acme"));
            assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("Saved.");
        }
    }

    @Test
    void renderManualSectionReturnsTheSupplierSectionFragmentWithTheManualRows() {
        // given
        SupplierConnectionViewFactory viewFactory = mock(SupplierConnectionViewFactory.class);
        SupplierConnectionView manual = new SupplierConnectionView(
                "manual:Hurtownia X", null, "Hurtownia X", ConnectionMode.MANUAL, false, false, false, null, true);
        when(viewFactory.views(any())).thenReturn(
                new SupplierConnectionViewFactory.SupplierConnectionViews(List.of(), List.of(manual)));
        ConcurrentModel model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = SupplierSectionModel.renderManualSection(viewFactory, store(), null, model);

            // then
            assertThat(view).contains("${sectionRows}, true, false,");
            assertThat(model.getAttribute("sectionRows")).isEqualTo(List.of(manual));
            assertThat(model.getAttribute("sectionSuccessMessage")).isNull();
        }
    }

    @Test
    void renderErrorFragmentSetsANon2xxStatusAndTheErrorMessageAttribute() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = SupplierSectionModel.renderErrorFragment("Something went wrong.", model, response);

        // then
        assertThat(view).isEqualTo("fragments/supplier-section :: sectionError");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("Something went wrong.");
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
