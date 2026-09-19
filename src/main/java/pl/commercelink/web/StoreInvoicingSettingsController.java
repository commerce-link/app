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
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.InvoicingSettingsForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;
import java.util.Map;

/**
 * Settings › Invoicing: the status of the invoicing system and the invoice settings form. The page carries one form:
 * the system is chosen and its access details entered on its own subpage ({@link StoreInvoicingSystemController}), so a
 * single "Save changes" can only mean the invoice settings. Bank accounts moved to Settings › Payments: invoices do not
 * read them, customers see the default one as transfer details. The store comes from the session (ADMIN) or the path
 * (SUPER_ADMIN), never from the form: the old page took it from a hidden field, so a store admin could overwrite another
 * store's default bank account, the one its customers transfer money to.
 */
@Controller
@RequiredArgsConstructor
public class StoreInvoicingSettingsController {

    private static final String VIEW = "store-invoicing";
    private static final String INVOICES_FRAGMENT = VIEW + " :: invoicesForm";

    private final StoresRepository storesRepository;
    private final InvoicingSystems invoicingSystems;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/invoicing")
    @PreAuthorize("hasRole('ADMIN')")
    public String invoicing(Model model) {
        return show(CustomSecurityContext.getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/invoicing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminInvoicing(@PathVariable String storeId, Model model) {
        return show(storeId, model);
    }

    @PostMapping("/dashboard/store/invoicing")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveInvoices(@ModelAttribute InvoicingSettingsForm form,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletResponse response) {
        return saveInvoices(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/invoicing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveInvoices(@PathVariable String storeId, @ModelAttribute InvoicingSettingsForm form,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletResponse response) {
        return saveInvoices(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    private String show(String storeId, Model model) {
        Store store = requireStore(storeId);
        render(store, InvoicingSettingsForm.from(store.getInvoicingConfiguration()), Map.of(), model);
        return VIEW;
    }

    private String saveInvoices(String storeId, InvoicingSettingsForm form, boolean async, Model model, Locale locale,
                                RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            render(store, form, errors, model);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return INVOICES_FRAGMENT;
            }
            return VIEW;
        }
        form.applyTo(store);
        storesRepository.save(store);
        String message = messageSource.getMessage("store.invoicing.update.success", null, locale);
        if (async) {
            render(store, InvoicingSettingsForm.from(store.getInvoicingConfiguration()), Map.of(), model);
            model.addAttribute("savedMessage", message);
            return INVOICES_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + SettingsPaths.store(storeId, "/invoicing");
    }

    private void render(Store store, InvoicingSettingsForm invoices, Map<String, String> invoicesErrors, Model model) {
        String storeId = store.getStoreId();
        model.addAttribute("systemStatus", invoicingSystems.status(store));
        model.addAttribute("systemHref", SettingsPaths.store(storeId, "/invoicing/system"));
        model.addAttribute("disconnectHref", SettingsPaths.store(storeId, "/invoicing/system/disconnect"));
        model.addAttribute("invoicesForm", invoices);
        model.addAttribute("invoicesErrors", invoicesErrors);
        model.addAttribute("invoicesAction", SettingsPaths.store(storeId, "/invoicing"));
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
