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
import java.util.Set;

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
            String view = SupplierSectionModel.renderExternalSection(
                    viewFactory, registry, store(), Set.of("Elko"), "Saved.", model);

            // then -- a no-argument view name: ThymeleafView rejects positional fragment parameters
            // in a view specification, so a regression back to the parameterized selector (with or
            // without a stray "(") is caught here rather than only at runtime
            assertThat(view).isEqualTo("fragments/supplier-section :: externalSection");
            assertThat(view).doesNotContain("(");
            assertThat(model.getAttribute("sectionRows")).isEqualTo(List.of(elko));
            assertThat(model.getAttribute("sectionShowMode")).isEqualTo(true);
            // Elko is already connected, so only Acme is left to offer in the Add dropdown
            assertThat(model.getAttribute("sectionAvailableSuppliers")).isEqualTo(List.of("Acme"));
            assertThat(model.getAttribute("sectionSuccessMessage")).isEqualTo("Saved.");
            assertThat(model.getAttribute("sectionSuppliersWithStoredConfig")).isEqualTo("Elko");
        }
    }

    @Test
    void renderExternalSectionRepublishesTheStoredConfigListFreshOnEveryCallInsteadOfOnlyAtPageLoad() {
        // The supplier modal lives outside #external-supplier-section and is never re-rendered by a
        // save, so the fix for a required password field staying required after a same-session
        // connect depends entirely on this model attribute being derived from whatever the caller
        // passes on *this* call, not fixed at some earlier render. A stale implementation that
        // ignores the parameter (e.g. hardcodes "" or reuses a value captured once) would report the
        // same set on both calls below and fail the second assertion.
        // given
        SupplierConnectionViewFactory viewFactory = mock(SupplierConnectionViewFactory.class);
        SupplierRegistry registry = mock(SupplierRegistry.class);
        when(viewFactory.views(any())).thenReturn(
                new SupplierConnectionViewFactory.SupplierConnectionViews(List.of(), List.of()));
        when(registry.getExternalSupplierNames()).thenReturn(List.of("Elko"));

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when -- simulates the page before any credentials were ever saved for Elko ...
            ConcurrentModel beforeSave = new ConcurrentModel();
            SupplierSectionModel.renderExternalSection(
                    viewFactory, registry, store(), Set.of(), null, beforeSave);

            // ... and the very next request in the same page session, right after the operator
            // connected Elko with credentials -- no reload, same modal markup
            ConcurrentModel afterSave = new ConcurrentModel();
            SupplierSectionModel.renderExternalSection(
                    viewFactory, registry, store(), Set.of("Elko"), "Supplier Elko saved.", afterSave);

            // then
            assertThat(beforeSave.getAttribute("sectionSuppliersWithStoredConfig")).isEqualTo("");
            assertThat(afterSave.getAttribute("sectionSuppliersWithStoredConfig")).isEqualTo("Elko");
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
            assertThat(view).isEqualTo("fragments/supplier-section :: manualSection");
            assertThat(view).doesNotContain("(");
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
