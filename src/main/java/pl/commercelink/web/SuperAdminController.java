package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreForm;
import pl.commercelink.stores.StoresRepository;

import java.util.*;

@PreAuthorize("hasRole('SUPER_ADMIN')")
@Controller
public class SuperAdminController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StoreCopyService storeCopyService;

    @Autowired
    private OrphanedProductCleanupService orphanedProductCleanupService;

    @GetMapping("/dashboard/stores")
    public String store(Model model) {
        List<Store> stores = storesRepository.findAll();
        model.addAttribute("stores", stores);
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

        return "store";
    }

    @GetMapping("/dashboard/store/create")
    public String createStorePage(Model model) {
        Store store = new Store();
        store.setStoreId(UniqueIdentifierGenerator.generate());
        model.addAttribute("store", store);
        return "store-create";
    }

    @PostMapping("/dashboard/store/create")
    public String createStore(@ModelAttribute Store store, Locale locale, RedirectAttributes redirectAttributes) {
        storesRepository.save(store);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.create.success", null, locale));
        return String.format("redirect:/dashboard/store/%s", store.getStoreId());
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
            System.err.println("[StoreCopy] Failed to copy store " + storeId + ": " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.copy.error", null, locale));
            return String.format("redirect:/dashboard/store/%s/copy", storeId);
        }
    }

    @PostMapping("/dashboard/stores/cleanup-products")
    public String cleanupProducts(Locale locale, RedirectAttributes redirectAttributes) {
        try {
            int deletedCount = orphanedProductCleanupService.cleanupOrphanedProducts();
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("store.cleanup.success", new Object[]{deletedCount}, locale));
        } catch (Exception e) {
            System.err.println("[OrphanedProductCleanup] Failed: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.cleanup.error", null, locale));
        }
        return "redirect:/dashboard/stores";
    }

}
