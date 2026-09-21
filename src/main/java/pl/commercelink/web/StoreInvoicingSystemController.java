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
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.IntegrationStatus;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings › Invoicing › Invoicing system: the system is chosen and its access details entered once, rarely changed and
 * partly secret, so they live on their own page instead of next to the everyday invoice settings. The store comes from
 * the session (ADMIN) or the path (SUPER_ADMIN): the old integration panel posted as ADMIN only, so the super admin got
 * a 403.
 */
@Controller
@RequiredArgsConstructor
public class StoreInvoicingSystemController {

    private static final String VIEW = "store-invoicing-system";
    private static final String FORM_FRAGMENT = VIEW + " :: systemForm";

    private final StoresRepository storesRepository;
    private final InvoicingSystems invoicingSystems;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/invoicing/system")
    @PreAuthorize("hasRole('ADMIN')")
    public String system(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/invoicing/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSystem(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/invoicing/system")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveSystem(@ModelAttribute IntegrationSettingsForm form,
                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes,
                             HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/invoicing/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveSystem(@PathVariable String storeId, @ModelAttribute IntegrationSettingsForm form,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       Model model, Locale locale, RedirectAttributes redirectAttributes,
                                       HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/invoicing/system/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDisconnect(Model model, Locale locale) {
        return confirmDisconnect(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/invoicing/system/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDisconnect(@PathVariable String storeId, Model model, Locale locale) {
        return confirmDisconnect(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/invoicing/system/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(CustomSecurityContext.getStoreId(), locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/invoicing/system/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDisconnect(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(storeId, locale, redirectAttributes);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        String providerName = invoicingSystems.current(store);
        IntegrationSettingsForm form = IntegrationSettingsForm.from(providerName, invoicingSystems.storedSettings(store),
                invoicingSystems.fieldsOf(providerName));
        return render(store, form, Map.of(), model, locale);
    }

    private String save(String storeId, IntegrationSettingsForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        List<ProviderField> fields = invoicingSystems.fieldsOf(form.getProviderName());
        Map<String, String> errors = form.validate(fields, invoicingSystems.storedSecretKeys(store, form.getProviderName()));
        if (!errors.isEmpty()) {
            String view = render(store, form, errors, model, locale);
            model.addAttribute("errorLabels", form.errorLabels(fields));
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }
        // Validated above, so the adapter settings are complete: the old panel switched the store to a provider whose
        // secret had not been created (saveConfiguration returned false) and still reported success.
        invoicingSystems.save(store, form.getProviderName(), form.toConfiguration(fields));
        storesRepository.save(store);

        String invoicingPath = SettingsPaths.store(storeId, "/invoicing");
        String message = messageSource.getMessage("store.invoicing.system.saved", null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, invoicingPath, message);
            render(store, form, Map.of(), model, locale);
            model.addAttribute("redirectTo", invoicingPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + invoicingPath;
    }

    private String confirmDisconnect(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        IntegrationStatus status = invoicingSystems.status(store);
        if (!status.chosen()) {
            return "redirect:" + SettingsPaths.store(storeId, "/invoicing");
        }
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.invoicing.system.disconnect.title", new Object[]{status.displayName()}, locale),
                messageSource.getMessage("store.invoicing.system.disconnect.message", null, locale),
                messageSource.getMessage("store.invoicing.system.disconnect.action", null, locale),
                SettingsPaths.store(storeId, "/invoicing/system/disconnect"),
                SettingsPaths.store(storeId, "/invoicing")));
        model.addAttribute("backLabel", messageSource.getMessage("store.invoicing", null, locale));
        return "settings-confirm";
    }

    private String disconnect(String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        String providerName = invoicingSystems.current(store);
        if (providerName != null) {
            invoicingSystems.disconnect(store, providerName);
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes,
                    messageSource.getMessage("store.invoicing.system.disconnected", null, locale));
        }
        return "redirect:" + SettingsPaths.store(storeId, "/invoicing");
    }

    private String render(Store store, IntegrationSettingsForm form, Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        List<InvoicingProviderDescriptor> providers = invoicingSystems.installed();
        IntegrationStatus status = invoicingSystems.status(store);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", Map.of());
        model.addAttribute("providers", providers);
        model.addAttribute("providerKnown", providers.stream().anyMatch(provider -> provider.name().equals(form.getProviderName())));
        model.addAttribute("storedSecretIds", invoicingSystems.storedSecretKeys(store, form.getProviderName()).stream()
                .map(key -> "setting-" + form.getProviderName() + "-" + key)
                .collect(Collectors.toSet()));
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/invoicing/system"));
        model.addAttribute("invoicingHref", SettingsPaths.store(storeId, "/invoicing"));
        model.addAttribute("backLabel", messageSource.getMessage("store.invoicing", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage(status.chosen()
                ? "store.invoicing.system.change.title" : "store.invoicing.system.choose.title", null, locale));
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
