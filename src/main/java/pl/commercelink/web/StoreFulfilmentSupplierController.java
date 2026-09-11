package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierSelectionForm;
import pl.commercelink.web.dtos.SupplierConnectionForm;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoreFulfilmentSupplierController {

    private final StoresRepository storesRepository;
    private final StoreSupplierConnectionService storeSupplierConnectionService;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/store/fulfilment/supplier")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute SupplierConnectionForm form, Locale locale, RedirectAttributes attributes) {
        return doSave(CustomSecurityContext.getStoreId(), form, locale, attributes);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment/supplier")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveForStore(@PathVariable String storeId, @ModelAttribute SupplierConnectionForm form,
                               Locale locale, RedirectAttributes attributes) {
        return doSave(storeId, form, locale, attributes);
    }

    @PostMapping("/dashboard/store/fulfilment/supplier/{supplierName}/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(@PathVariable String supplierName, Locale locale, RedirectAttributes attributes) {
        return doDisconnect(CustomSecurityContext.getStoreId(), supplierName, locale, attributes);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment/supplier/{supplierName}/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String disconnectForStore(@PathVariable String storeId, @PathVariable String supplierName,
                                     Locale locale, RedirectAttributes attributes) {
        return doDisconnect(storeId, supplierName, locale, attributes);
    }

    private String doSave(String storeId, SupplierConnectionForm form, Locale locale, RedirectAttributes attributes) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            attributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale));
            return redirect(storeId, null);
        }
        SupplierSelectionForm selection = new SupplierSelectionForm(
                form.getSupplierName(), form.getMode(),
                form.isIncludeInPricing(), form.isIncludeInFulfilment());

        StoreSupplierConnectionService.ConnectionUpdateResult result =
                storeSupplierConnectionService.connectOrUpdate(store, selection, form.getConfiguration());
        if (result.hasErrors()) {
            attributes.addFlashAttribute("errorMessage", result.errors().stream()
                    .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                    .collect(Collectors.joining(" ")));
            // Carried back so the reopened modal can restore what the operator typed.
            attributes.addFlashAttribute("submittedSupplierConfiguration", form.getConfiguration());
            attributes.addFlashAttribute("submittedIncludeInPricing", form.isIncludeInPricing());
            attributes.addFlashAttribute("submittedIncludeInFulfilment", form.isIncludeInFulfilment());
            return redirect(storeId, form.getSupplierName());
        }
        attributes.addFlashAttribute("successMessage",
                messageSource.getMessage("store.fulfilment.supplier.saved", new Object[]{form.getSupplierName()}, locale));
        return redirect(storeId, null);
    }

    private String doDisconnect(String storeId, String supplierName, Locale locale, RedirectAttributes attributes) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            attributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale));
            return redirect(storeId, null);
        }
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                storeSupplierConnectionService.disconnect(store, supplierName);
        if (result.hasErrors()) {
            attributes.addFlashAttribute("errorMessage", result.errors().stream()
                    .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                    .collect(Collectors.joining(" ")));
            return redirect(storeId, null);
        }
        attributes.addFlashAttribute("successMessage",
                messageSource.getMessage("store.fulfilment.supplier.disconnected", new Object[]{supplierName}, locale));
        return redirect(storeId, null);
    }

    private String redirect(String storeId, String editSupplier) {
        String base = CustomSecurityContext.hasRole("SUPER_ADMIN")
                ? String.format("redirect:/dashboard/store/%s/fulfilment", storeId)
                : "redirect:/dashboard/store/fulfilment";
        return editSupplier == null
                ? base
                : base + "?edit=" + URLEncoder.encode(editSupplier, StandardCharsets.UTF_8);
    }
}
