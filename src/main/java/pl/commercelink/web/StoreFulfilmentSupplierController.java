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
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierSelectionForm;
import pl.commercelink.web.dtos.SupplierConnectionForm;

import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoreFulfilmentSupplierController {

    private final StoresRepository storesRepository;
    private final StoreSupplierConnectionService storeSupplierConnectionService;
    private final SupplierConnectionViewFactory supplierConnectionViewFactory;
    private final SupplierRegistry supplierRegistry;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/store/fulfilment/supplier")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute SupplierConnectionForm form, Locale locale, Model model,
                       HttpServletResponse response) {
        return doSave(CustomSecurityContext.getStoreId(), form, locale, model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment/supplier")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveForStore(@PathVariable String storeId, @ModelAttribute SupplierConnectionForm form,
                               Locale locale, Model model, HttpServletResponse response) {
        return doSave(storeId, form, locale, model, response);
    }

    @PostMapping("/dashboard/store/fulfilment/supplier/{supplierName}/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(@PathVariable String supplierName, Locale locale, Model model,
                             HttpServletResponse response) {
        return doDisconnect(CustomSecurityContext.getStoreId(), supplierName, locale, model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment/supplier/{supplierName}/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String disconnectForStore(@PathVariable String storeId, @PathVariable String supplierName,
                                     Locale locale, Model model, HttpServletResponse response) {
        return doDisconnect(storeId, supplierName, locale, model, response);
    }

    private String doSave(String storeId, SupplierConnectionForm form, Locale locale, Model model,
                          HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return SupplierSectionModel.renderErrorFragment(
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale), model, response);
        }
        SupplierSelectionForm selection = new SupplierSelectionForm(
                form.getSupplierName(), form.getMode(),
                form.isIncludeInPricing(), form.isIncludeInFulfilment());

        StoreSupplierConnectionService.ConnectionUpdateResult result =
                storeSupplierConnectionService.connectOrUpdate(store, selection, form.getConfiguration());
        if (result.hasErrors()) {
            String message = result.errors().stream()
                    .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                    .collect(Collectors.joining(" "));
            return SupplierSectionModel.renderErrorFragment(message, model, response);
        }
        String successMessage = messageSource.getMessage(
                "store.fulfilment.supplier.saved", new Object[]{form.getSupplierName()}, locale);
        return SupplierSectionModel.renderExternalSection(
                supplierConnectionViewFactory, supplierRegistry, store, successMessage, model);
    }

    private String doDisconnect(String storeId, String supplierName, Locale locale, Model model,
                                HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return SupplierSectionModel.renderErrorFragment(
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale), model, response);
        }
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                storeSupplierConnectionService.disconnect(store, supplierName);
        if (result.hasErrors()) {
            String message = result.errors().stream()
                    .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                    .collect(Collectors.joining(" "));
            return SupplierSectionModel.renderErrorFragment(message, model, response);
        }
        String successMessage = messageSource.getMessage(
                "store.fulfilment.supplier.disconnected", new Object[]{supplierName}, locale);
        return SupplierSectionModel.renderExternalSection(
                supplierConnectionViewFactory, supplierRegistry, store, successMessage, model);
    }
}
