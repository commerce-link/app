package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.printing.PrintProviderRegistry;
import pl.commercelink.printing.api.PrintProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.web.dtos.WarehousePrinterForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Label printers of the store, added and edited on their own page below Settings › Warehouse. Available to the store
 * admin and to the super admin like every other store setting; the store comes from the session or the path.
 */
@Controller
@RequiredArgsConstructor
public class StoreWarehousePrinterController {

    private static final String VIEW = "store-warehouse-printer";
    private static final String FORM_FRAGMENT = VIEW + " :: printerForm";

    /** A printer type offered on the form, with the settings its adapter asks for. */
    public record PrinterType(String name, String displayName, List<ProviderField> fields) {
    }

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;
    private final PrintProviderRegistry printProviderRegistry;

    @GetMapping("/dashboard/store/warehouse/printers/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newPrinter(@RequestParam(required = false) String type, Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), type, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse/printers/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewPrinter(@PathVariable String storeId, @RequestParam(required = false) String type,
                                       Model model, Locale locale) {
        return showNew(storeId, type, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse/printers/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createPrinter(@ModelAttribute WarehousePrinterForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), null, form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/printers/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreatePrinter(@PathVariable String storeId, @ModelAttribute WarehousePrinterForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, null, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/warehouse/printers/{printerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editPrinter(@PathVariable String printerId, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), printerId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse/printers/{printerId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditPrinter(@PathVariable String storeId, @PathVariable String printerId, Model model, Locale locale) {
        return showEdit(storeId, printerId, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse/printers/{printerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updatePrinter(@PathVariable String printerId, @ModelAttribute WarehousePrinterForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), printerId, form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/printers/{printerId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdatePrinter(@PathVariable String storeId, @PathVariable String printerId,
                                          @ModelAttribute WarehousePrinterForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, printerId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/dashboard/store/warehouse/printers/{printerId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String printerId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), printerId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse/printers/{printerId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String printerId, Model model, Locale locale) {
        return confirmDelete(storeId, printerId, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse/printers/{printerId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deletePrinter(@PathVariable String printerId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), printerId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/printers/{printerId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDeletePrinter(@PathVariable String storeId, @PathVariable String printerId, Locale locale,
                                          RedirectAttributes redirectAttributes) {
        return delete(storeId, printerId, locale, redirectAttributes);
    }

    private String showNew(String storeId, String type, Model model, Locale locale) {
        requireStore(storeId);
        List<PrinterType> types = printerTypes();
        String preselected = types.size() == 1 ? types.getFirst().name() : type;
        return render(storeId, null, WarehousePrinterForm.empty(preselected), Map.of(), model, locale);
    }

    private String showEdit(String storeId, String printerId, Model model, Locale locale) {
        Printer printer = requirePrinter(requireStore(storeId), printerId);
        return render(storeId, printer, WarehousePrinterForm.from(printer, fieldsOf(printer.getProviderName())), Map.of(), model, locale);
    }

    private String save(String storeId, String printerId, WarehousePrinterForm form, boolean async, Model model,
                        Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                        HttpServletResponse response) {
        Store store = requireStore(storeId);
        if (store.getWarehouseConfiguration() == null) {
            store.setWarehouseConfiguration(new WarehouseConfiguration());
        }
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();
        Printer existing = printerId == null ? null : requirePrinter(store, printerId);
        List<ProviderField> fields = fieldsOf(form.getType());

        Map<String, String> errors = form.validate(fields, configuration, existing);
        if (!errors.isEmpty()) {
            String view = render(storeId, existing, form, errors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        Printer printer = existing != null ? existing : new Printer();
        form.applyTo(printer, fields);
        if (existing == null) {
            printer.setId(UUID.randomUUID().toString());
            configuration.addPrinter(printer);
        }
        storesRepository.save(store);

        String warehousePath = SettingsPaths.store(storeId, "/warehouse");
        String message = messageSource.getMessage(existing == null ? "store.warehouse.printer.added" : "store.warehouse.printer.updated",
                null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, warehousePath, message);
            render(storeId, printer, form, Map.of(), model, locale);
            model.addAttribute("redirectTo", warehousePath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + warehousePath;
    }

    private String confirmDelete(String storeId, String printerId, Model model, Locale locale) {
        Printer printer = requirePrinter(requireStore(storeId), printerId);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.warehouse.printer.delete.title", new Object[]{printer.getName()}, locale),
                messageSource.getMessage("store.warehouse.printer.delete.message", null, locale),
                messageSource.getMessage("store.warehouse.printer.delete.action", null, locale),
                SettingsPaths.store(storeId, "/warehouse/printers/" + printerId + "/delete"),
                SettingsPaths.store(storeId, "/warehouse")));
        model.addAttribute("backLabel", messageSource.getMessage("store.warehouse", null, locale));
        return "settings-confirm";
    }

    private String delete(String storeId, String printerId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();
        if (configuration != null && configuration.findPrinter(printerId).isPresent()) {
            configuration.removePrinter(printerId);
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.warehouse.printer.deleted", null, locale));
        }
        return "redirect:" + SettingsPaths.store(storeId, "/warehouse");
    }

    private String render(String storeId, Printer existing, WarehousePrinterForm form, Map<String, String> errors,
                          Model model, Locale locale) {
        String basePath = SettingsPaths.store(storeId, "/warehouse/printers");
        List<PrinterType> types = printerTypes();
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", form.errorLabels(fieldsOf(form.getType())));
        model.addAttribute("types", types);
        model.addAttribute("knownType", types.stream().anyMatch(printerType -> printerType.name().equals(form.getType())));
        model.addAttribute("formAction", existing == null ? basePath + "/new" : basePath + "/" + existing.getId());
        model.addAttribute("storedSecretIds", storedSecretIds(existing));
        model.addAttribute("pageTitle", messageSource.getMessage(existing == null
                ? "store.warehouse.printer.new.title" : "store.warehouse.printer.edit.title", null, locale));
        model.addAttribute("editing", existing != null);
        model.addAttribute("warehouseHref", SettingsPaths.store(storeId, "/warehouse"));
        model.addAttribute("backLabel", messageSource.getMessage("store.warehouse", null, locale));
        return VIEW;
    }

    /** Ids of secret inputs whose value is stored: the page says so instead of sending the value back. */
    private Set<String> storedSecretIds(Printer existing) {
        if (existing == null || existing.getSettings() == null) {
            return Set.of();
        }
        List<ProviderField> fields = fieldsOf(existing.getProviderName());
        if (fields == null) {
            return Set.of();
        }
        return fields.stream()
                .filter(field -> field.type() == FieldType.PASSWORD)
                .filter(field -> StringUtils.isNotBlank(existing.getSettings().get(field.key())))
                .map(field -> WarehousePrinterForm.fieldId(existing.getProviderName(), field))
                .collect(Collectors.toSet());
    }

    private List<PrinterType> printerTypes() {
        return printProviderRegistry.availableProviders().stream()
                .map(descriptor -> new PrinterType(descriptor.name(), descriptor.displayName(), descriptor.configurationFields()))
                .toList();
    }

    private List<ProviderField> fieldsOf(String type) {
        if (type == null) {
            return null;
        }
        PrintProviderDescriptor descriptor = printProviderRegistry.getDescriptor(type);
        return descriptor != null ? descriptor.configurationFields() : null;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private Printer requirePrinter(Store store, String printerId) {
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();
        return (configuration == null ? java.util.Optional.<Printer>empty() : configuration.findPrinter(printerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
