package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Renders the external and manual supplier sections of the store fulfilment screen as Thymeleaf
 * fragments (see fragments/supplier-section.html). Shared by the initial page render
 * ({@link StoreController}) and the async mutating endpoints ({@link StoreFulfilmentSupplierController},
 * {@link ManualSupplierController}) that swap just one section back in after a connect, edit,
 * disconnect, save or delete instead of redirecting and reloading the whole page.
 *
 * <p>The async endpoints return no-argument view names ({@code externalSection} / {@code
 * manualSection}) rather than the parameterized {@code supplierSection(...)} selector: Spring's
 * {@code ThymeleafView} rejects a view name carrying positional fragment parameters (they are only
 * legal inside a {@code th:replace}/{@code th:insert} expression), so every value the fragment
 * needs is published as a model attribute instead and the wrapper fragments read it from there.
 */
public final class SupplierSectionModel {

    private SupplierSectionModel() {
    }

    public static String basePath(String storeId) {
        return CustomSecurityContext.hasRole("SUPER_ADMIN") ? "/dashboard/store/" + storeId : "/dashboard/store";
    }

    public static String renderExternalSection(SupplierConnectionViewFactory supplierConnectionViewFactory,
                                               SupplierRegistry supplierRegistry, Store store,
                                               Set<String> suppliersWithStoredConfig,
                                               String successMessage, Model model) {
        SupplierConnectionViewFactory.SupplierConnectionViews views = supplierConnectionViewFactory.views(store);
        Set<String> connected = views.external().stream()
                .map(SupplierConnectionView::identity)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        List<String> availableSuppliers = supplierRegistry.getExternalSupplierNames().stream()
                .filter(name -> !connected.contains(name))
                .toList();
        model.addAttribute("sectionRows", views.external());
        model.addAttribute("sectionShowMode", store.canUseGlobalSuppliers());
        model.addAttribute("sectionAvailableSuppliers", availableSuppliers);
        model.addAttribute("sectionSuccessMessage", successMessage);
        // Republished on every render (like sectionShowMode) rather than left for the modal's
        // page-load attribute: a save can be the very thing that puts a supplier into this set, and
        // the modal lives outside this section and is never re-rendered, so the fix relies on the
        // JS reading this fresh value off the swapped-in root on every open instead.
        model.addAttribute("sectionSuppliersWithStoredConfig", String.join(";", suppliersWithStoredConfig));
        // No-argument view name: ThymeleafView (unlike th:replace/th:insert) rejects a view name
        // carrying positional fragment parameters, so everything the fragment needs travels as a
        // model attribute and the controller selects a wrapper fragment that has none.
        return "fragments/supplier-section :: externalSection";
    }

    public static String renderManualSection(SupplierConnectionViewFactory supplierConnectionViewFactory,
                                             Store store, String successMessage, Model model) {
        SupplierConnectionViewFactory.SupplierConnectionViews views = supplierConnectionViewFactory.views(store);
        model.addAttribute("sectionRows", views.manual());
        model.addAttribute("sectionSuccessMessage", successMessage);
        return "fragments/supplier-section :: manualSection";
    }

    public static String renderErrorFragment(String message, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("errorMessage", message);
        return "fragments/supplier-section :: sectionError";
    }
}
