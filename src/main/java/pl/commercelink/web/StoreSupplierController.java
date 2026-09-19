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
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.dtos.SupplierSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.ProviderFieldText;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Settings › Suppliers › a supplier: adding one (an integration or a price list uploaded by hand), editing it and
 * disconnecting or deleting it, each on its own page. Replaces the modals of the old fulfilment page, which one script
 * shared between every connection. The store comes from the session (ADMIN) or the path (SUPER_ADMIN), the supplier from
 * the path; a supplier of another store is not found.
 */
@Controller
@RequiredArgsConstructor
public class StoreSupplierController {

    private static final String VIEW = "store-supplier";
    private static final String FORM_FRAGMENT = VIEW + " :: supplierForm";
    // Identities are type names, "Type-token" or legacy "manual:Name" keys.
    private static final String IDENTITY = "/{identity:[A-Za-z0-9_.:-]+}";

    private final StoresRepository storesRepository;
    private final SupplierConnections suppliers;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/suppliers/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newSupplier(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/suppliers/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewSupplier(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/suppliers/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String addSupplier(@ModelAttribute("form") SupplierSettingsForm form,
                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                              Model model, Locale locale, RedirectAttributes redirectAttributes,
                              HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), null, form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/suppliers/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminAddSupplier(@PathVariable String storeId, @ModelAttribute("form") SupplierSettingsForm form,
                                        @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                        Model model, Locale locale, RedirectAttributes redirectAttributes,
                                        HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, null, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/dashboard/store/suppliers" + IDENTITY)
    @PreAuthorize("hasRole('ADMIN')")
    public String editSupplier(@PathVariable String identity, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), identity, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/suppliers" + IDENTITY)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditSupplier(@PathVariable String storeId, @PathVariable String identity, Model model,
                                         Locale locale) {
        return showEdit(storeId, identity, model, locale);
    }

    @PostMapping("/dashboard/store/suppliers" + IDENTITY)
    @PreAuthorize("hasRole('ADMIN')")
    public String updateSupplier(@PathVariable String identity, @ModelAttribute("form") SupplierSettingsForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), identity, form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/suppliers" + IDENTITY)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateSupplier(@PathVariable String storeId, @PathVariable String identity,
                                           @ModelAttribute("form") SupplierSettingsForm form,
                                           @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                           Model model, Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, identity, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping({"/dashboard/store/suppliers" + IDENTITY + "/disconnect", "/dashboard/store/suppliers" + IDENTITY + "/delete"})
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmRemove(@PathVariable String identity, Model model, Locale locale) {
        return confirmRemove(CustomSecurityContext.getStoreId(), identity, model, locale);
    }

    @GetMapping({"/dashboard/store/{storeId}/suppliers" + IDENTITY + "/disconnect",
            "/dashboard/store/{storeId}/suppliers" + IDENTITY + "/delete"})
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmRemove(@PathVariable String storeId, @PathVariable String identity, Model model,
                                          Locale locale) {
        return confirmRemove(storeId, identity, model, locale);
    }

    @PostMapping({"/dashboard/store/suppliers" + IDENTITY + "/disconnect", "/dashboard/store/suppliers" + IDENTITY + "/delete"})
    @PreAuthorize("hasRole('ADMIN')")
    public String remove(@PathVariable String identity, Locale locale, RedirectAttributes redirectAttributes) {
        return remove(CustomSecurityContext.getStoreId(), identity, locale, redirectAttributes);
    }

    @PostMapping({"/dashboard/store/{storeId}/suppliers" + IDENTITY + "/disconnect",
            "/dashboard/store/{storeId}/suppliers" + IDENTITY + "/delete"})
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminRemove(@PathVariable String storeId, @PathVariable String identity, Locale locale,
                                   RedirectAttributes redirectAttributes) {
        return remove(storeId, identity, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        List<String> types = suppliers.types();
        // With a single integration there is still the price list to choose from, so nothing is preselected unless
        // integrations are missing altogether.
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier(types.isEmpty() ? SupplierSettingsForm.CSV : null);
        return render(store, null, form, Map.of(), null, model, locale);
    }

    private String showEdit(String storeId, String identity, Model model, Locale locale) {
        Store store = requireStore(storeId);
        StoreSupplierConnection connection = requireConnection(store, identity);
        if (!suppliers.known(connection)) {
            // The integration is gone: nothing to edit, the list offers only disconnecting it.
            return "redirect:" + suppliersPath(storeId);
        }
        SupplierSettingsForm form = suppliers.formOf(store, connection);
        if (!store.canUseGlobalSuppliers()) {
            // A global connection of a store that may no longer use the global configuration is completed as an own one.
            form.setMode(ConnectionMode.OWN.name());
        }
        return render(store, connection, form, Map.of(), null, model, locale);
    }

    private String save(String storeId, String identity, SupplierSettingsForm form, boolean async, Model model,
                        Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                        HttpServletResponse response) {
        Store store = requireStore(storeId);
        StoreSupplierConnection existing = identity == null ? null : requireConnection(store, identity);
        if (existing != null) {
            if (!suppliers.known(existing)) {
                return "redirect:" + suppliersPath(storeId);
            }
            // What is edited is the supplier in the address, whatever the form says.
            form.setProviderName(existing.getMode() == ConnectionMode.MANUAL
                    ? SupplierSettingsForm.CSV : SupplierIdentity.typeOf(identity));
        }
        if (!form.csv() && !canChooseMode(store, existing)) {
            form.setMode(existing != null && existing.getMode() == ConnectionMode.GLOBAL && store.canUseGlobalSuppliers()
                    ? ConnectionMode.GLOBAL.name() : ConnectionMode.OWN.name());
        }

        SupplierConnections.SaveResult result = form.csv()
                ? suppliers.saveCsv(store, existing, form, locale)
                : suppliers.saveIntegration(store, existing, form, locale);
        if (!result.ok()) {
            String view = render(store, existing, form, result.errors(), result.failure(), model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        String name = form.global() ? form.getProviderName() : form.getLabel().trim();
        String message = messageSource.getMessage(existing == null ? "store.suppliers.added" : "store.suppliers.saved",
                new Object[]{name}, locale);
        String next = suppliersPath(storeId);
        if (async) {
            SettingsFlash.forNextPage(request, response, next, message);
            render(store, existing, form, Map.of(), null, model, locale);
            model.addAttribute("redirectTo", next);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + next;
    }

    private String confirmRemove(String storeId, String identity, Model model, Locale locale) {
        Store store = requireStore(storeId);
        StoreSupplierConnection connection = requireConnection(store, identity);
        boolean csv = connection.getMode() == ConnectionMode.MANUAL;
        String label = SupplierLabels.labelOf(connection);
        String path = suppliersPath(storeId) + "/" + identity + (csv ? "/delete" : "/disconnect");
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage(csv ? "store.suppliers.delete.title" : "store.suppliers.disconnect.title",
                        new Object[]{label}, locale),
                messageSource.getMessage(csv ? "store.manual.delete.confirm" : "store.supplier.disconnect.confirm", null, locale),
                messageSource.getMessage(csv ? "store.suppliers.delete.action" : "store.suppliers.disconnect.action", null, locale),
                path, suppliersPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.suppliers", null, locale));
        return "settings-confirm";
    }

    private String remove(String storeId, String identity, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        StoreSupplierConnection connection = requireConnection(store, identity);
        boolean csv = connection.getMode() == ConnectionMode.MANUAL;
        String label = SupplierLabels.labelOf(connection);
        if (suppliers.remove(store, connection)) {
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage(
                    csv ? "store.suppliers.deleted" : "store.suppliers.disconnected", new Object[]{label}, locale));
        } else {
            // The services restore what they changed; nothing was removed, so no success message.
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.supplier.connection.error.update.failed", null, locale));
        }
        return "redirect:" + suppliersPath(storeId);
    }

    private String render(Store store, StoreSupplierConnection existing, SupplierSettingsForm form,
                          Map<String, String> errors, String failure, Model model, Locale locale) {
        String storeId = store.getStoreId();
        String identity = existing == null ? null : existing.getSupplierName();
        List<String> types = existing == null ? suppliers.types()
                : existing.getMode() == ConnectionMode.MANUAL ? List.of() : List.of(SupplierIdentity.typeOf(identity));
        Map<String, List<ProviderField>> fields = new LinkedHashMap<>();
        types.forEach(type -> fields.put(type, suppliers.fieldsOf(type)));
        Set<String> storedSecrets = suppliers.storedSecretKeys(store, existing != null && existing.getMode() == ConnectionMode.OWN
                ? identity : null);

        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", errorLabels(form, fields.get(form.getProviderName())));
        model.addAttribute("failure", failure);
        model.addAttribute("editing", existing != null);
        model.addAttribute("types", types);
        model.addAttribute("csvOption", existing == null);
        model.addAttribute("providerKnown", form.csv() || (form.getProviderName() != null && types.contains(form.getProviderName())));
        model.addAttribute("fields", fields);
        model.addAttribute("storedSecretIds", storedSecrets.stream()
                .map(key -> "setting-" + form.getProviderName() + "-" + key)
                .collect(Collectors.toSet()));
        model.addAttribute("canChooseMode", canChooseMode(store, existing));
        // Switching an own connection to the global configuration deletes its saved access details and feed file.
        model.addAttribute("warnLeavingOwn", existing != null && existing.getMode() == ConnectionMode.OWN);
        model.addAttribute("globalNotAllowed", existing != null && existing.getMode() == ConnectionMode.GLOBAL
                && !store.canUseGlobalSuppliers());
        model.addAttribute("hasFeed", existing != null && existing.getMode() == ConnectionMode.MANUAL
                && suppliers.hasFeed(store, identity));
        model.addAttribute("scheduleMinIntervalMinutes", suppliers.minIntervalMinutes());
        model.addAttribute("formAction", suppliersPath(storeId) + (existing == null ? "/new" : "/" + identity));
        model.addAttribute("suppliersHref", suppliersPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.suppliers", null, locale));
        model.addAttribute("pageTitle", existing == null
                ? messageSource.getMessage("store.suppliers.new.title", null, locale)
                : SupplierLabels.labelOf(existing));
        return VIEW;
    }

    /** Own or global: only in a store that may use the global configuration, and never for a tokened own identity. */
    private static boolean canChooseMode(Store store, StoreSupplierConnection existing) {
        if (!store.canUseGlobalSuppliers()) {
            return false;
        }
        return existing == null || (existing.getMode() != ConnectionMode.MANUAL
                && !SupplierIdentity.hasToken(existing.getSupplierName()));
    }

    private static Map<String, String> errorLabels(SupplierSettingsForm form, List<ProviderField> fields) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach(field -> labels.put(IntegrationSettingsForm.fieldId(form.getProviderName(), field),
                    ProviderFieldText.label(field)));
        }
        return labels;
    }

    private static String suppliersPath(String storeId) {
        return SettingsPaths.store(storeId, "/suppliers");
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private StoreSupplierConnection requireConnection(Store store, String identity) {
        StoreSupplierConnection connection = suppliers.connection(store, identity);
        if (connection == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return connection;
    }
}
