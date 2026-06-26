package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.io.IOException;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class ManualSupplierController {

    private final ManualSupplierService manualSupplierService;
    private final org.springframework.context.MessageSource messageSource;

    @PostMapping("/dashboard/store/manual-supplier")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@RequestParam("name") String name, Locale locale, RedirectAttributes redirectAttributes) {
        flash(manualSupplierService.create(currentStoreId(), name), locale, redirectAttributes);
        return "redirect:/dashboard/store/fulfilment";
    }

    @PostMapping("/dashboard/store/manual-supplier/{identity}/feed")
    @PreAuthorize("hasRole('ADMIN')")
    public String upload(@PathVariable String identity, @RequestParam("file") MultipartFile file,
                         Locale locale, RedirectAttributes redirectAttributes) throws IOException {
        ManualSupplierService.Result result = file.isEmpty()
                ? ManualSupplierService.Result.error("store.manual.error.csv.empty")
                : manualSupplierService.uploadFeed(currentStoreId(), identity, file.getBytes());
        flash(result, locale, redirectAttributes);
        return "redirect:/dashboard/store/fulfilment";
    }

    @PostMapping("/dashboard/store/manual-supplier/{identity}/flags")
    @PreAuthorize("hasRole('ADMIN')")
    public String flags(@PathVariable String identity,
                        @RequestParam(value = "includeInPricing", defaultValue = "false") boolean includeInPricing,
                        @RequestParam(value = "includeInFulfilment", defaultValue = "false") boolean includeInFulfilment,
                        Locale locale, RedirectAttributes redirectAttributes) {
        flash(manualSupplierService.setFlags(currentStoreId(), identity, includeInPricing, includeInFulfilment),
                locale, redirectAttributes);
        return "redirect:/dashboard/store/fulfilment";
    }

    @PostMapping("/dashboard/store/manual-supplier/{identity}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable String identity, RedirectAttributes redirectAttributes) {
        manualSupplierService.delete(currentStoreId(), identity);
        return "redirect:/dashboard/store/fulfilment";
    }

    private void flash(ManualSupplierService.Result result, Locale locale, RedirectAttributes redirectAttributes) {
        if (result.ok()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("store.manual.success", null, locale));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.messageCode(), null, locale));
        }
    }

    private String currentStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
