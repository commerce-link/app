package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ManualSupplierController {

    private final ManualSupplierService manualSupplierService;
    private final StoresRepository storesRepository;
    private final SupplierConnectionViewFactory supplierConnectionViewFactory;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/store/manual-supplier")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(@RequestParam("name") String name, Locale locale) {
        return doCreate(currentStoreId(), name, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/manual-supplier")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createForStore(@PathVariable String storeId, @RequestParam("name") String name, Locale locale) {
        return doCreate(storeId, name, locale);
    }

    @PostMapping("/dashboard/store/manual-supplier/{identity}/feed")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFeed(@PathVariable String identity,
                                                          @RequestParam("file") MultipartFile file,
                                                          Locale locale) throws IOException {
        return doUploadFeed(currentStoreId(), identity, file, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/manual-supplier/{identity}/feed")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFeedForStore(@PathVariable String storeId,
                                                                  @PathVariable String identity,
                                                                  @RequestParam("file") MultipartFile file,
                                                                  Locale locale) throws IOException {
        return doUploadFeed(storeId, identity, file, locale);
    }

    @PostMapping("/dashboard/store/manual-supplier/{identity}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable String identity, Locale locale, Model model, HttpServletResponse response) {
        return doDelete(currentStoreId(), identity, locale, model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/manual-supplier/{identity}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteForStore(@PathVariable String storeId, @PathVariable String identity, Locale locale,
                                 Model model, HttpServletResponse response) {
        return doDelete(storeId, identity, locale, model, response);
    }

    private ResponseEntity<Map<String, Object>> doCreate(String storeId, String name, Locale locale) {
        ManualSupplierService.Result result = manualSupplierService.create(storeId, name);
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", messageSource.getMessage(result.messageCode(), null, locale)));
        }
        String identity = ManualSupplierInfos.identityFor(name.trim());
        return ResponseEntity.ok(Map.of("ok", true, "identity", identity, "label", ManualSupplierInfos.label(identity)));
    }

    private ResponseEntity<Map<String, Object>> doUploadFeed(String storeId, String identity, MultipartFile file, Locale locale) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false,
                    "message", messageSource.getMessage("store.manual.error.csv.empty", null, locale)));
        }
        ManualSupplierService.Result result = manualSupplierService.uploadFeed(storeId, identity, file.getBytes());
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false,
                    "message", messageSource.getMessage(result.messageCode(), null, locale)));
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return ResponseEntity.ok(Map.of("ok", true, "fileName", fileName));
    }

    private String doDelete(String storeId, String identity, Locale locale, Model model, HttpServletResponse response) {
        ManualSupplierService.Result result = manualSupplierService.delete(storeId, identity);
        if (!result.ok()) {
            return SupplierSectionModel.renderErrorFragment(
                    messageSource.getMessage(result.messageCode(), null, locale), model, response);
        }
        Store store = storesRepository.findById(storeId);
        String successMessage = messageSource.getMessage(
                "store.manual.deleted", new Object[]{ManualSupplierInfos.label(identity)}, locale);
        return SupplierSectionModel.renderManualSection(supplierConnectionViewFactory, store, successMessage, model);
    }

    @PostMapping("/dashboard/store/fulfilment/manual-supplier/{identity}")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveSelection(@PathVariable String identity,
                                @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                                @RequestParam(name = "includeInPricing", defaultValue = "false") boolean includeInPricing,
                                @RequestParam(name = "includeInFulfilment", defaultValue = "false") boolean includeInFulfilment,
                                Locale locale, Model model, HttpServletResponse response) {
        return doSaveSelection(currentStoreId(), identity, enabled, includeInPricing, includeInFulfilment, locale,
                model, response);
    }

    @PostMapping("/dashboard/store/{storeId}/fulfilment/manual-supplier/{identity}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveSelectionForStore(@PathVariable String storeId, @PathVariable String identity,
                                        @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                                        @RequestParam(name = "includeInPricing", defaultValue = "false") boolean includeInPricing,
                                        @RequestParam(name = "includeInFulfilment", defaultValue = "false") boolean includeInFulfilment,
                                        Locale locale, Model model, HttpServletResponse response) {
        return doSaveSelection(storeId, identity, enabled, includeInPricing, includeInFulfilment, locale, model,
                response);
    }

    private String doSaveSelection(String storeId, String identity, boolean enabled,
                                   boolean includeInPricing, boolean includeInFulfilment,
                                   Locale locale, Model model, HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return SupplierSectionModel.renderErrorFragment(
                    messageSource.getMessage("store.manual.error.store.notfound", null, locale), model, response);
        }
        manualSupplierService.applySelections(storeId, List.of(
                new ManualSupplierService.ManualSelection(identity, enabled, includeInPricing, includeInFulfilment)));
        Store updated = storesRepository.findById(storeId);
        String successMessage = messageSource.getMessage(
                "store.fulfilment.supplier.saved", new Object[]{ManualSupplierInfos.label(identity)}, locale);
        return SupplierSectionModel.renderManualSection(supplierConnectionViewFactory, updated, successMessage, model);
    }

    @GetMapping("/dashboard/store/fulfilment/manual-supplier/section")
    @PreAuthorize("hasRole('ADMIN')")
    public String section(Model model) {
        return doRenderSection(currentStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/fulfilment/manual-supplier/section")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String sectionForStore(@PathVariable String storeId, Model model) {
        return doRenderSection(storeId, model);
    }

    // Used only to refresh the manual section after the create/upload-feed JSON endpoints below
    // succeed: those stay JSON (they are already fetch-driven), so this is what lets the page
    // show the new/updated row without a full reload.
    private String doRenderSection(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        return SupplierSectionModel.renderManualSection(supplierConnectionViewFactory, store, null, model);
    }

    private String currentStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
