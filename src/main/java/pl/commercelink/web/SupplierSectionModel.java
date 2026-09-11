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
 */
public final class SupplierSectionModel {

    private SupplierSectionModel() {
    }

    public static String basePath(String storeId) {
        return CustomSecurityContext.hasRole("SUPER_ADMIN") ? "/dashboard/store/" + storeId : "/dashboard/store";
    }

    public static String renderExternalSection(SupplierConnectionViewFactory supplierConnectionViewFactory,
                                               SupplierRegistry supplierRegistry, Store store,
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
        model.addAttribute("sectionBasePath", basePath(store.getStoreId()));
        model.addAttribute("sectionAvailableSuppliers", availableSuppliers);
        model.addAttribute("sectionSuccessMessage", successMessage);
        return "fragments/supplier-section :: supplierSection(${sectionRows}, false, ${sectionShowMode}, "
                + "${sectionBasePath}, 'store.supplier.section.title', 'supplier-add-button', "
                + "'store.supplier.add.button', ${sectionAvailableSuppliers.isEmpty()}, "
                + "'store.supplier.add.none', ${sectionSuccessMessage})";
    }

    public static String renderManualSection(SupplierConnectionViewFactory supplierConnectionViewFactory,
                                             Store store, String successMessage, Model model) {
        SupplierConnectionViewFactory.SupplierConnectionViews views = supplierConnectionViewFactory.views(store);
        model.addAttribute("sectionRows", views.manual());
        model.addAttribute("sectionBasePath", basePath(store.getStoreId()));
        model.addAttribute("sectionSuccessMessage", successMessage);
        return "fragments/supplier-section :: supplierSection(${sectionRows}, true, false, ${sectionBasePath}, "
                + "'store.manual.section.title', 'manual-add-button', 'store.manual.add.button', "
                + "false, null, ${sectionSuccessMessage})";
    }

    public static String renderErrorFragment(String message, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("errorMessage", message);
        return "fragments/supplier-section :: sectionError";
    }
}
