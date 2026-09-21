package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.SupplierAdminSettingsForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.SupplierView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings › Suppliers: every supplier of the store (integrations and price lists uploaded by hand) with what the store
 * uses it for and what stopped it from working, and, for the application admin, the store's supplier settings only they
 * change. Adding, editing and removing a supplier happen on its own page ({@link StoreSupplierController}).
 */
@Controller
@RequiredArgsConstructor
public class StoreSuppliersSettingsController {

    private static final String VIEW = "store-suppliers";
    private static final String ADMIN_FORM_FRAGMENT = VIEW + " :: adminForm";

    private final StoresRepository storesRepository;
    private final SupplierConnections suppliers;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/suppliers")
    @PreAuthorize("hasRole('ADMIN')")
    public String suppliers(Model model, Locale locale) {
        Store store = requireStore(CustomSecurityContext.getStoreId());
        return render(store, null, Map.of(), null, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/suppliers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSuppliers(@PathVariable String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        return render(store, SupplierAdminSettingsForm.from(store), Map.of(), null, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/suppliers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveAdminSettings(@PathVariable String storeId, @ModelAttribute SupplierAdminSettingsForm form,
                                    @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                    Model model, Locale locale, RedirectAttributes redirectAttributes,
                                    HttpServletRequest request, HttpServletResponse response) {
        boolean async = SettingsPaths.isAsync(requestedWith);
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        String failure = null;
        if (errors.isEmpty() && !suppliers.saveAdminSettings(store, form.isCanUseGlobalSuppliers(), form.cacheTtlMinutes())) {
            failure = messageSource.getMessage("store.supplier.connection.error.update.failed", null, locale);
        }
        if (!errors.isEmpty() || failure != null) {
            String view = render(store, form, errors, failure, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return ADMIN_FORM_FRAGMENT;
            }
            return view;
        }
        String message = messageSource.getMessage("store.suppliers.admin.saved", null, locale);
        if (async) {
            // The rows depend on these settings (a global connection is incomplete once the store may not use the global
            // configuration), so the page reloads instead of swapping only the form.
            String next = SettingsPaths.store(storeId, "/suppliers");
            SettingsFlash.forNextPage(request, response, next, message);
            render(store, SupplierAdminSettingsForm.from(store), Map.of(), null, model, locale);
            model.addAttribute("redirectTo", next);
            return ADMIN_FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + SettingsPaths.store(storeId, "/suppliers");
    }

    private String render(Store store, SupplierAdminSettingsForm adminForm, Map<String, String> errors, String failure,
                          Model model, Locale locale) {
        String suppliersPath = SettingsPaths.store(store.getStoreId(), "/suppliers");
        List<SupplierView> views = suppliers.views(store, suppliersPath);
        Map<String, String> removeMessages = new LinkedHashMap<>();
        views.forEach(view -> removeMessages.put(view.identity(), messageSource.getMessage(
                view.csv() ? "store.manual.delete.confirm" : "store.supplier.disconnect.confirm", null, locale)));
        model.addAttribute("suppliers", views);
        model.addAttribute("removeMessages", removeMessages);
        model.addAttribute("newSupplierHref", suppliersPath + "/new");
        model.addAttribute("adminForm", adminForm);
        model.addAttribute("errors", errors);
        model.addAttribute("failure", failure);
        model.addAttribute("adminFormAction", suppliersPath);
        return VIEW;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
