package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivationService;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreForm;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.TrialStatus;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;

import java.util.*;

@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Controller
@RequiredArgsConstructor
public class SuperAdminController {

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;
    private final StoreCopyService storeCopyService;
    private final OrphanedProductCleanupService orphanedProductCleanupService;
    private final StoreDeletionService storeDeletionService;
    private final StoreCreationService storeCreationService;
    private final StoreSettingsOverviewFactory storeSettingsOverviewFactory;
    private final StoreTrialService storeTrialService;
    private final StoreActivity storeActivity;
    private final StoreActivationService storeActivationService;

    @GetMapping("/dashboard/stores")
    public String store(@RequestParam(defaultValue = "desc") String dir, Model model) {
        boolean ascending = "asc".equalsIgnoreCase(dir);
        Comparator<String> order = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<Store> stores = storesRepository.findAll().stream()
                .sorted(Comparator.comparing(Store::getCreatedAt, Comparator.nullsLast(order)))
                .toList();
        Map<String, TrialStatus> trials = new HashMap<>();
        Map<String, DeactivationStatus> deactivations = new HashMap<>();
        stores.forEach(store -> {
            storeTrialService.status(store).ifPresent(status -> trials.put(store.getStoreId(), status));
            storeActivity.status(store).ifPresent(status -> deactivations.put(store.getStoreId(), status));
        });
        model.addAttribute("stores", stores);
        model.addAttribute("trials", trials);
        model.addAttribute("deactivations", deactivations);
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
                              @RequestParam(required = false) String apiKey,
                              Locale locale,
                              RedirectAttributes redirectAttributes) {
        try {
            Store store = storeCreationService.createStore(CreateStoreRequest.bare(name, apiKey));
            redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.create.success", null, locale));
            return String.format("redirect:/dashboard/store/%s", store.getStoreId());
        } catch (Exception e) {
            log.error("Failed to create store '{}'", name, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.create.error", null, locale));
            return "redirect:/dashboard/store/create";
        }
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

    @PostMapping("/dashboard/store/{storeId}/trial/convert")
    public String convertTrial(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        try {
            if (storeTrialService.convertToFullAccount(storeId)) {
                redirectAttributes.addFlashAttribute("successMessage",
                        messageSource.getMessage("store.trial.convert.success", null, locale));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("store.trial.missing", null, locale));
            }
        } catch (RuntimeException e) {
            log.error("Failed to convert the trial of store {} to a full account", storeId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.trial.error", null, locale));
        }
        return "redirect:/dashboard/stores";
    }

    @PostMapping("/dashboard/store/{storeId}/deactivate")
    public String deactivateStore(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        try {
            flashActivationOutcome(storeActivationService.deactivate(storeId), "store.activity.deactivate.success",
                    locale, redirectAttributes);
        } catch (RuntimeException e) {
            log.error("Failed to deactivate store {}", storeId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.activity.error", null, locale));
        }
        return "redirect:/dashboard/stores";
    }

    @PostMapping("/dashboard/store/{storeId}/activate")
    public String activateStore(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        try {
            flashActivationOutcome(storeActivationService.activate(storeId), "store.activity.activate.success",
                    locale, redirectAttributes);
        } catch (RuntimeException e) {
            log.error("Failed to activate store {}", storeId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.activity.error", null, locale));
        }
        return "redirect:/dashboard/stores";
    }

    private void flashActivationOutcome(StoreActivationService.Outcome outcome, String successKey, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        switch (outcome) {
            case CHANGED -> redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage(successKey, null, locale));
            case UNCHANGED -> redirectAttributes.addFlashAttribute("warningMessage",
                    messageSource.getMessage("store.activity.unchanged", null, locale));
            case MISSING -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.activity.missing", null, locale));
            case TRIAL_ENDED -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.activity.activate.trial-ended", null, locale));
        }
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
