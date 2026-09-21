package pl.commercelink.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreApiKeyService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreForm;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;

import java.util.*;

@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Controller
public class SuperAdminController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private StoreApiKeyService storeApiKeyService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StoreCopyService storeCopyService;

    @Autowired
    private OrphanedProductCleanupService orphanedProductCleanupService;

    @Autowired
    private StoreDeletionService storeDeletionService;

    @Autowired
    private StoreCreationService storeCreationService;

    @Autowired
    private StoreSettingsOverviewFactory storeSettingsOverviewFactory;

    @GetMapping("/dashboard/stores")
    public String store(@RequestParam(defaultValue = "desc") String dir, Model model) {
        boolean ascending = "asc".equalsIgnoreCase(dir);
        Comparator<String> order = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<Store> stores = storesRepository.findAll().stream()
                .sorted(Comparator.comparing(Store::getCreatedAt, Comparator.nullsLast(order)))
                .toList();
        model.addAttribute("stores", stores);
        model.addAttribute("dir", ascending ? "asc" : "desc");
        return "stores";
    }

    @GetMapping("/dashboard/store/{storeId}")
    public String superAdminStore(@PathVariable String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        StoreForm form = new StoreForm(store);
        model.addAttribute("form", form);
        model.addAttribute("isSuperAdmin", true);
        model.addAttribute("overview", storeSettingsOverviewFactory.build(store, UserRole.SUPER_ADMIN));

        return "store";
    }

    @GetMapping("/dashboard/store/create")
    public String createStorePage(Model model) {
        model.addAttribute("store", new Store());
        return "store-create";
    }

    @PostMapping("/dashboard/store/create")
    public String createStore(@RequestParam String name,
                              Locale locale,
                              RedirectAttributes redirectAttributes) {
        try {
            Store store = storeCreationService.createStore(CreateStoreRequest.bare(name));
            redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.create.success", null, locale));
            redirectAttributes.addFlashAttribute("generatedApiKey", store.getPlaintextApiKey());
            return String.format("redirect:/dashboard/store/%s", store.getStoreId());
        } catch (Exception e) {
            log.error("Failed to create store '{}'", name, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.create.error", null, locale));
            return "redirect:/dashboard/store/create";
        }
    }

    @PostMapping("/dashboard/store/{storeId}/regenerate-api-key")
    public String regenerateApiKey(@PathVariable String storeId,
                                   Locale locale,
                                   RedirectAttributes redirectAttributes) {
        try {
            String apiKey = storeApiKeyService.regenerate(storeId);
            redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.apikey.regenerated", null, locale));
            redirectAttributes.addFlashAttribute("generatedApiKey", apiKey);
        } catch (Exception e) {
            log.error("Failed to regenerate API key for store '{}'", storeId, e);
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("store.apikey.error", null, locale));
        }
        return String.format("redirect:/dashboard/store/%s", storeId);
    }

    @GetMapping("/dashboard/store/{storeId}/copy")
    public String copyStorePage(@PathVariable String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        model.addAttribute("store", store);
        model.addAttribute("suggestedName", store.getName() + " (kopia)");
        return "store-copy";
    }

    @PostMapping("/dashboard/store/{storeId}/copy")
    public String copyStore(@PathVariable String storeId,
                            @RequestParam String newStoreName,
                            Locale locale,
                            RedirectAttributes redirectAttributes) {
        try {
            Store newStore = storeCopyService.copyStore(storeId, newStoreName);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("store.copy.success", new Object[]{newStoreName}, locale));
            return String.format("redirect:/dashboard/store/%s", newStore.getStoreId());
        } catch (Exception e) {
            log.error("Failed to copy store {}", storeId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.copy.error", null, locale));
            return String.format("redirect:/dashboard/store/%s/copy", storeId);
        }
    }

    @PostMapping("/dashboard/store/{storeId}/delete")
    public String deleteStore(@PathVariable String storeId,
                              @RequestParam(required = false) String confirmStoreId,
                              Locale locale, RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.delete.missing", null, locale));
            return "redirect:/dashboard/stores";
        }
        if (store.getDemo() == null && (confirmStoreId == null || !storeId.equals(confirmStoreId.trim()))) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.delete.confirm.mismatch", null, locale));
            return "redirect:/dashboard/stores";
        }
        try {
            if (storeDeletionService.deleteStore(storeId, StoreDeletionService.Guard.ANY)) {
                redirectAttributes.addFlashAttribute("successMessage",
                        messageSource.getMessage("store.delete.success", null, locale));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("store.delete.error", null, locale));
            }
        } catch (Exception e) {
            log.error("Failed to delete store {}", storeId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.delete.error", null, locale));
        }
        return "redirect:/dashboard/stores";
    }

    @PostMapping("/dashboard/stores/cleanup-products")
    public String cleanupProducts(Locale locale, RedirectAttributes redirectAttributes) {
        try {
            int deletedCount = orphanedProductCleanupService.cleanupOrphanedProducts();
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("store.cleanup.success", new Object[]{deletedCount}, locale));
        } catch (Exception e) {
            log.error("Orphaned product cleanup failed", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.cleanup.error", null, locale));
        }
        return "redirect:/dashboard/stores";
    }

}
