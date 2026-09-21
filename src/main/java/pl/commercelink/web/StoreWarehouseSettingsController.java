package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.printing.PrintProviderRegistry;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.web.dtos.WarehouseDocumentsForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.WarehouseAddressView;
import pl.commercelink.web.settings.WarehousePrinterView;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings › Warehouse: the documents form and the lists of goods-receiving addresses and label printers, each with
 * its own save. The store comes from the session (ADMIN) or the path (SUPER_ADMIN), never from the form.
 */
@Controller
@RequiredArgsConstructor
public class StoreWarehouseSettingsController {

    private static final String VIEW = "store-warehouse";
    private static final String DOCUMENTS_FRAGMENT = VIEW + " :: documentsForm";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;
    private final PrintProviderRegistry printProviderRegistry;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    @GetMapping("/dashboard/store/warehouse")
    @PreAuthorize("hasRole('ADMIN')")
    public String warehouse(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminWarehouse(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveDocuments(@ModelAttribute WarehouseDocumentsForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveDocuments(@PathVariable String storeId, @ModelAttribute WarehouseDocumentsForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    private String show(String storeId, Model model, Locale locale) {
        if (storesRepository.findById(storeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Records are edited and deleted by id; addresses saved by the old table form, and printers, have none yet.
        // Another save of the store may land meanwhile, so the ids are assigned on a fresh copy with a retry.
        AtomicBoolean assigned = new AtomicBoolean();
        Store store = optimisticLockingExecutor.modifyAndSave(
                () -> storesRepository.findById(storeId),
                fresh -> {
                    boolean addresses = fresh.assignMissingShippingDetailsIds();
                    boolean printers = fresh.getWarehouseConfiguration() != null
                            && fresh.getWarehouseConfiguration().assignMissingPrinterIds();
                    assigned.set(addresses || printers);
                },
                fresh -> {
                    if (assigned.get()) {
                        storesRepository.save(fresh);
                    }
                });
        return render(store, WarehouseDocumentsForm.from(store), Map.of(), model, locale);
    }

    private String save(String storeId, WarehouseDocumentsForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            String view = render(store, form, errors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return DOCUMENTS_FRAGMENT;
            }
            return view;
        }

        form.applyTo(store);
        storesRepository.save(store);
        String successMessage = messageSource.getMessage("store.warehouse.documents.update.success", null, locale);
        if (async) {
            render(store, WarehouseDocumentsForm.from(store), Map.of(), model, locale);
            model.addAttribute("savedMessage", successMessage);
            return DOCUMENTS_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, successMessage);
        return "redirect:" + SettingsPaths.store(storeId, "/warehouse");
    }

    private String render(Store store, WarehouseDocumentsForm form, Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        String addressesPath = SettingsPaths.store(storeId, "/warehouse/addresses");
        String printersPath = SettingsPaths.store(storeId, "/warehouse/printers");
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();

        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/warehouse"));
        model.addAttribute("invoicingConnected", store.hasIntegration(IntegrationType.INVOICING_PROVIDER));
        model.addAttribute("invoicingHref", SettingsPaths.store(storeId, "/invoicing"));
        model.addAttribute("addresses", store.getShippingDetails().stream()
                .map(details -> WarehouseAddressView.of(details, locale, addressesPath))
                .toList());
        model.addAttribute("newAddressHref", addressesPath + "/new");
        model.addAttribute("printers", configuration == null ? List.of() : configuration.getPrinters().stream()
                .map(printer -> WarehousePrinterView.of(printer, printProviderRegistry.getDescriptor(printer.getProviderName()), printersPath))
                .toList());
        model.addAttribute("newPrinterHref", printersPath + "/new");
        model.addAttribute("printerTypesAvailable", !printProviderRegistry.availableProviders().isEmpty());
        return VIEW;
    }
}
