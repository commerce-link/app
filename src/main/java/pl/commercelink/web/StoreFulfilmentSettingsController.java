package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Saves the fulfilment settings panel (assembly/realization days, automated fulfilment, default
 * fulfilment type and, for a super admin, the global-suppliers flag and inventory cache TTL)
 * asynchronously, the same way {@link StoreFulfilmentSupplierController} saves a supplier
 * connection: the response is the re-rendered section, swapped in without a page reload.
 */
@Controller
@RequiredArgsConstructor
public class StoreFulfilmentSettingsController {

    private final StoresRepository storesRepository;
    private final StoreSupplierConnectionService storeSupplierConnectionService;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/store/fulfilment/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute FulfilmentSettingsForm form, Locale locale, Model model,
                       HttpServletResponse response) {
        return doSave(CustomSecurityContext.getStoreId(), form, locale, model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment/settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveForStore(@PathVariable String storeId, @ModelAttribute FulfilmentSettingsForm form,
                               Locale locale, Model model, HttpServletResponse response) {
        return doSave(storeId, form, locale, model, response);
    }

    private String doSave(String storeId, FulfilmentSettingsForm form, Locale locale, Model model,
                          HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return SupplierSectionModel.renderErrorFragment(
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale), model, response);
        }
        boolean isSuperAdmin = CustomSecurityContext.hasRole("SUPER_ADMIN");
        // Captured before the save so it can be compared against the post-save value below --
        // applyStoreSettings mutates this same Store instance in place (via
        // StoreSupplierConnectionPersister.saveStore), so re-reading it afterwards is enough,
        // no second lookup needed.
        boolean canUseGlobalSuppliersBefore = store.canUseGlobalSuppliers();

        StoreSupplierConnectionService.ConnectionUpdateResult result = storeSupplierConnectionService
                .applyStoreSettings(store, form.toFulfilmentConfiguration(), isSuperAdmin);
        if (result.hasErrors()) {
            String message = result.errors().stream()
                    .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                    .collect(Collectors.joining(" "));
            return SupplierSectionModel.renderErrorFragment(message, model, response);
        }

        // The external suppliers section's mode column and the supplier modal's mode selector
        // both depend on canUseGlobalSuppliers; a save that flips it must tell the page to refresh
        // that section too, but a save that only touched e.g. the day counts must not -- that
        // would refresh (and flicker) the suppliers table on every unrelated settings save.
        boolean refreshExternalSuppliers = canUseGlobalSuppliersBefore != store.canUseGlobalSuppliers();
        String successMessage = messageSource.getMessage("store.fulfilment.settings.update.success", null, locale);
        return FulfilmentSettingsSectionModel.renderSection(
                store, isSuperAdmin, successMessage, refreshExternalSuppliers, model);
    }
}
