package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.inventory.supplier.manual.ManualSupplierNames;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ManualSupplierController {

    private final ManualSupplierService manualSupplierService;
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
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String identity, Locale locale) {
        return doDelete(currentStoreId(), identity, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/manual-supplier/{identity}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteForStore(@PathVariable String storeId, @PathVariable String identity, Locale locale) {
        return doDelete(storeId, identity, locale);
    }

    private ResponseEntity<Map<String, Object>> doCreate(String storeId, String name, Locale locale) {
        ManualSupplierService.Result result = manualSupplierService.create(storeId, name);
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", messageSource.getMessage(result.messageCode(), null, locale)));
        }
        String identity = ManualSupplierNames.identityFor(name.trim());
        return ResponseEntity.ok(Map.of("ok", true, "identity", identity, "label", ManualSupplierNames.label(identity)));
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

    private ResponseEntity<Map<String, Object>> doDelete(String storeId, String identity, Locale locale) {
        ManualSupplierService.Result result = manualSupplierService.delete(storeId, identity);
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", messageSource.getMessage(result.messageCode(), null, locale)));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private String currentStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
