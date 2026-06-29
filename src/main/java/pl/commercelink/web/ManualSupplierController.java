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
import pl.commercelink.inventory.supplier.manual.ManualSupplierNames;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.starter.security.CustomSecurityContext;

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
        ManualSupplierService.Result result = manualSupplierService.create(currentStoreId(), name);
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", messageSource.getMessage(result.messageCode(), null, locale)));
        }
        String identity = ManualSupplierNames.identityFor(name.trim());
        return ResponseEntity.ok(Map.of("ok", true, "identity", identity, "label", ManualSupplierNames.label(identity)));
    }

    @PostMapping("/dashboard/store/manual-supplier/{identity}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String identity, Locale locale) {
        ManualSupplierService.Result result = manualSupplierService.delete(currentStoreId(), identity);
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", messageSource.getMessage(result.messageCode(), null, locale)));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private String currentStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
