package pl.commercelink.web;

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
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;
import java.util.Map;

/**
 * Settings › Order fulfilment: delivery time, how new orders are fulfilled and the customer's order page, as one form
 * posted back to the page's address. Suppliers have their own page ({@link StoreSuppliersSettingsController}). The store
 * comes from the session (ADMIN) or the path (SUPER_ADMIN), never from the form.
 */
@Controller
@RequiredArgsConstructor
public class StoreFulfilmentSettingsController {

    private static final String VIEW = "store-fulfilment";
    private static final String FORM_FRAGMENT = VIEW + " :: fulfilmentForm";

    private final StoresRepository storesRepository;
    private final StoreSupplierConnectionService storeSupplierConnectionService;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/fulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String fulfilment(Model model) {
        Store store = requireStore(CustomSecurityContext.getStoreId());
        return render(store, FulfilmentSettingsForm.from(store), Map.of(), null, model);
    }

    @GetMapping("/dashboard/store/{storeId}/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminFulfilment(@PathVariable String storeId, Model model) {
        Store store = requireStore(storeId);
        return render(store, FulfilmentSettingsForm.from(store), Map.of(), null, model);
    }

    @PostMapping("/dashboard/store/fulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute FulfilmentSettingsForm form,
                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                       Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSave(@PathVariable String storeId, @ModelAttribute FulfilmentSettingsForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    private String save(String storeId, FulfilmentSettingsForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        String failure = null;
        if (errors.isEmpty()) {
            boolean isSuperAdmin = CustomSecurityContext.hasRole("SUPER_ADMIN");
            if (storeSupplierConnectionService.applyStoreSettings(store, form.toFulfilmentConfiguration(store), isSuperAdmin)
                    .hasErrors()) {
                failure = messageSource.getMessage("store.supplier.connection.error.update.failed", null, locale);
            }
        }
        if (!errors.isEmpty() || failure != null) {
            String view = render(store, form, errors, failure, model);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        String message = messageSource.getMessage("store.fulfilment.settings.update.success", null, locale);
        if (async) {
            render(store, FulfilmentSettingsForm.from(store), Map.of(), null, model);
            model.addAttribute("savedMessage", message);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + SettingsPaths.store(storeId, "/fulfilment");
    }

    private String render(Store store, FulfilmentSettingsForm form, Map<String, String> errors, String failure, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("failure", failure);
        model.addAttribute("formAction", SettingsPaths.store(store.getStoreId(), "/fulfilment"));
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
