package pl.commercelink.web;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivationService;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreForm;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.StoreCopyForm;
import pl.commercelink.web.dtos.StoreCreateForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;
import pl.commercelink.web.stores.StoresListPageFactory;
import pl.commercelink.web.stores.StoresListQuery;
import pl.commercelink.web.stores.StoresNotice;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    private final StoresListPageFactory storesListPageFactory;
    private final StoreActivationService storeActivationService;

    @GetMapping("/dashboard/stores")
    public String store(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                        @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                        @RequestParam(required = false) String delete, Model model) {
        StoresListQuery query = StoresListQuery.of(status, q, sort, dir, delete);
        model.addAttribute("page", storesListPageFactory.build(storesRepository.findAll(), query));
        return "stores";
    }

    /** Without JavaScript the menu links open these pages; with it, confirm-dialog.js posts to the same address. */
    @GetMapping("/dashboard/store/{storeId}/trial/convert")
    public String confirmConvertTrial(@PathVariable String storeId, @RequestParam(required = false) String back,
                                      Model model, Locale locale) {
        Store store = existing(storeId);
        return confirmPage(model, locale, message("stores.list.convert.title", locale, StoresListPageFactory.displayName(store)),
                message("store.trial.convert.confirm", locale), message("store.trial.convert.button", locale),
                "/dashboard/store/" + storeId + "/trial/convert", back, false);
    }

    @GetMapping("/dashboard/store/{storeId}/deactivate")
    public String confirmDeactivate(@PathVariable String storeId, @RequestParam(required = false) String back,
                                    Model model, Locale locale) {
        Store store = existing(storeId);
        return confirmPage(model, locale, message("stores.list.deactivate.title", locale, StoresListPageFactory.displayName(store)),
                message("store.activity.deactivate.confirm", locale), message("store.activity.deactivate.button", locale),
                "/dashboard/store/" + storeId + "/deactivate", back, false);
    }

    @GetMapping("/dashboard/store/{storeId}/activate")
    public String confirmActivate(@PathVariable String storeId, @RequestParam(required = false) String back,
                                  Model model, Locale locale) {
        Store store = existing(storeId);
        return confirmPage(model, locale, message("stores.list.activate.title", locale, StoresListPageFactory.displayName(store)),
                message("store.activity.activate.confirm", locale), message("store.activity.activate.button", locale),
                "/dashboard/store/" + storeId + "/activate", back, false);
    }

    /** A demo store is deleted after a plain confirmation; any other store needs its ID typed in the list's dialog. */
    @GetMapping("/dashboard/store/{storeId}/delete")
    public String confirmDelete(@PathVariable String storeId, @RequestParam(required = false) String back,
                                Model model, Locale locale) {
        Store store = existing(storeId);
        if (store.getDemo() == null) {
            return "redirect:" + StoresListQuery.withDeleteParam(back, storeId);
        }
        return confirmPage(model, locale, message("stores.list.delete.title", locale, StoresListPageFactory.displayName(store)),
                message("store.delete.confirm", locale), message("stores.list.delete.action", locale),
                "/dashboard/store/" + storeId + "/delete", back, true);
    }

    @GetMapping("/dashboard/stores/cleanup-products")
    public String confirmCleanupProducts(@RequestParam(required = false) String back, Model model, Locale locale) {
        return confirmPage(model, locale, message("stores.list.cleanup.title", locale),
                message("stores.list.cleanup.message", locale), message("store.cleanup.button", locale),
                "/dashboard/stores/cleanup-products", back, true);
    }

    private String confirmPage(Model model, Locale locale, String title, String text, String label, String actionPath,
                               String back, boolean destructive) {
        String safeBack = StoresListQuery.safeBack(back);
        model.addAttribute("confirm", new ConfirmAction(title, text, label,
                actionPath + "?back=" + URLEncoder.encode(safeBack, StandardCharsets.UTF_8), safeBack, destructive));
        model.addAttribute("backLabel", message("nav.stores", locale));
        return "settings-confirm";
    }

    private Store existing(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private String message(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args.length == 0 ? null : args, locale);
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
    public String createStorePage(@RequestParam(required = false) String back, Model model) {
        return renderCreate(StoreCreateForm.withId(storeCreationService.freeStoreId()), Map.of(), null, back, model);
    }

    @PostMapping("/dashboard/store/create")
    public String createStore(@ModelAttribute("form") StoreCreateForm form, @RequestParam(required = false) String back,
                              Model model, Locale locale, RedirectAttributes redirectAttributes) {
        // a form opened before this page handed out ids (or a tampered one) simply gets a fresh id
        if (!form.hasWellFormedId()) {
            form.setNewStoreId(storeCreationService.freeStoreId());
        }
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return renderCreate(form, errors, null, back, model);
        }
        Store taken = storesRepository.findById(form.getNewStoreId());
        if (taken != null) {
            return alreadyCreated(taken, form, back, model, locale, redirectAttributes);
        }
        try {
            Store store = storeCreationService.createStore(
                    CreateStoreRequest.bare(form.getNewStoreId(), form.trimmedName(), form.apiKeySuffix()));
            SettingsFlash.onRedirect(redirectAttributes, message("store.create.success", locale, store.getName()));
            return "redirect:/dashboard/store/" + store.getStoreId();
        } catch (ConditionalCheckFailedException e) {
            // the same form sent twice at once: the other request saved the store first (Store is versioned)
            Store raced = storesRepository.findById(form.getNewStoreId());
            if (raced != null) {
                return alreadyCreated(raced, form, back, model, locale, redirectAttributes);
            }
            log.error("Store id {} refused as taken but not readable", form.getNewStoreId(), e);
            return renderCreate(form, Map.of(), "store.create.error", back, model);
        } catch (RuntimeException e) {
            log.error("Failed to create store '{}'", form.trimmedName(), e);
            return renderCreate(form, Map.of(), "store.create.error", back, model);
        }
    }

    private String alreadyCreated(Store taken, StoreCreateForm form, String back, Model model, Locale locale,
                                  RedirectAttributes redirectAttributes) {
        if (sameName(taken, form.trimmedName())) {
            SettingsFlash.onRedirect(redirectAttributes, message("store.create.duplicate", locale, taken.getName()));
            return "redirect:/dashboard/store/" + taken.getStoreId();
        }
        form.setNewStoreId(storeCreationService.freeStoreId());
        return renderCreate(form, Map.of(), "store.create.error.stale", back, model);
    }

    private String renderCreate(StoreCreateForm form, Map<String, String> errors, String formError, String back, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("formError", formError);
        model.addAttribute("back", back == null ? null : StoresListQuery.safeBack(back));
        model.addAttribute("backHref", StoresListQuery.safeBack(back));
        return "store-create";
    }

    private static boolean sameName(Store store, String name) {
        return Objects.equals(StringUtils.strip(store.getName()), name);
    }

    @GetMapping("/dashboard/store/{storeId}/copy")
    public String copyStorePage(@PathVariable String storeId, @RequestParam(required = false) String back, Model model,
                                Locale locale) {
        Store source = existing(storeId);
        StoreCopyForm form = StoreCopyForm.of(message("store.copy.name.suggested", locale, source.getName()),
                storeCreationService.freeStoreId());
        return renderCopy(source, form, Map.of(), null, back, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/copy")
    public String copyStore(@PathVariable String storeId, @ModelAttribute("form") StoreCopyForm form,
                            @RequestParam(required = false) String back, Model model, Locale locale,
                            RedirectAttributes redirectAttributes) {
        Store source = existing(storeId);
        if (!form.hasWellFormedId()) {
            form.setNewStoreId(storeCreationService.freeStoreId());
        }
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return renderCopy(source, form, errors, null, back, model, locale);
        }
        Store taken = storesRepository.findById(form.getNewStoreId());
        if (taken != null) {
            return copyAlreadyMade(source, taken, form, back, model, locale, redirectAttributes);
        }
        try {
            Store copy = storeCopyService.copyStore(storeId, form.getNewStoreId(), form.trimmedName());
            SettingsFlash.onRedirect(redirectAttributes, message("store.copy.success", locale, copy.getName(), source.getName()));
            return "redirect:/dashboard/store/" + copy.getStoreId();
        } catch (ConditionalCheckFailedException e) {
            // the store is saved before its catalogs, so the request that lost the race copied nothing
            Store raced = storesRepository.findById(form.getNewStoreId());
            if (raced != null) {
                return copyAlreadyMade(source, raced, form, back, model, locale, redirectAttributes);
            }
            log.error("Copy id {} refused as taken but not readable", form.getNewStoreId(), e);
            return renderCopy(source, form, Map.of(), "store.copy.error", back, model, locale);
        } catch (RuntimeException e) {
            log.error("Failed to copy store {} as {}", storeId, form.getNewStoreId(), e);
            return renderCopy(source, form, Map.of(), "store.copy.error", back, model, locale);
        }
    }

    private String copyAlreadyMade(Store source, Store taken, StoreCopyForm form, String back, Model model, Locale locale,
                                   RedirectAttributes redirectAttributes) {
        if (sameName(taken, form.trimmedName())) {
            SettingsFlash.onRedirect(redirectAttributes, message("store.copy.duplicate", locale, taken.getName()));
            return "redirect:/dashboard/store/" + taken.getStoreId();
        }
        form.setNewStoreId(storeCreationService.freeStoreId());
        return renderCopy(source, form, Map.of(), "store.copy.error.stale", back, model, locale);
    }

    /** Without back the page was opened from the source store's settings, so it goes back there. */
    private String renderCopy(Store source, StoreCopyForm form, Map<String, String> errors, String formError, String back,
                              Model model, Locale locale) {
        model.addAttribute("source", source);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("formError", formError);
        model.addAttribute("back", back == null ? null : StoresListQuery.safeBack(back));
        model.addAttribute("backHref", back == null ? "/dashboard/store/" + source.getStoreId() : StoresListQuery.safeBack(back));
        model.addAttribute("backLabel", message(back == null ? "store.title" : "nav.stores", locale));
        return "store-copy";
    }

    @PostMapping("/dashboard/store/{storeId}/delete")
    public String deleteStore(@PathVariable String storeId,
                              @RequestParam(required = false) String confirmStoreId,
                              @RequestParam(required = false) String back,
                              Locale locale, RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.delete.missing", null, locale)));
            return "redirect:" + StoresListQuery.safeBack(back);
        }
        if (store.getDemo() == null && (confirmStoreId == null || !storeId.equals(confirmStoreId.trim()))) {
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.delete.confirm.mismatch", null, locale)));
            return "redirect:" + StoresListQuery.safeBack(back);
        }
        try {
            if (storeDeletionService.deleteStore(storeId, StoreDeletionService.Guard.ANY)) {
                redirectAttributes.addFlashAttribute("storesNotice",
                        StoresNotice.ok(messageSource.getMessage("store.delete.success", new Object[]{StoresListPageFactory.displayName(store)}, locale)));
            } else {
                redirectAttributes.addFlashAttribute("storesNotice",
                        StoresNotice.bad(messageSource.getMessage("store.delete.error", null, locale)));
            }
        } catch (Exception e) {
            log.error("Failed to delete store {}", storeId, e);
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.delete.error", null, locale)));
        }
        return "redirect:" + StoresListQuery.safeBack(back);
    }

    @PostMapping("/dashboard/store/{storeId}/trial/convert")
    public String convertTrial(@PathVariable String storeId, @RequestParam(required = false) String back,
                               Locale locale, RedirectAttributes redirectAttributes) {
        try {
            if (storeTrialService.convertToFullAccount(storeId)) {
                redirectAttributes.addFlashAttribute("storesNotice",
                        StoresNotice.ok(messageSource.getMessage("store.trial.convert.success", null, locale)));
            } else {
                redirectAttributes.addFlashAttribute("storesNotice",
                        StoresNotice.bad(messageSource.getMessage("store.trial.missing", null, locale)));
            }
        } catch (RuntimeException e) {
            log.error("Failed to convert the trial of store {} to a full account", storeId, e);
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.trial.error", null, locale)));
        }
        return "redirect:" + StoresListQuery.safeBack(back);
    }

    @PostMapping("/dashboard/store/{storeId}/deactivate")
    public String deactivateStore(@PathVariable String storeId, @RequestParam(required = false) String back,
                                  Locale locale, RedirectAttributes redirectAttributes) {
        try {
            flashActivationOutcome(storeActivationService.deactivate(storeId), "store.activity.deactivate.success",
                    locale, redirectAttributes);
        } catch (RuntimeException e) {
            log.error("Failed to deactivate store {}", storeId, e);
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.activity.error", null, locale)));
        }
        return "redirect:" + StoresListQuery.safeBack(back);
    }

    @PostMapping("/dashboard/store/{storeId}/activate")
    public String activateStore(@PathVariable String storeId, @RequestParam(required = false) String back,
                                Locale locale, RedirectAttributes redirectAttributes) {
        try {
            flashActivationOutcome(storeActivationService.activate(storeId), "store.activity.activate.success",
                    locale, redirectAttributes);
        } catch (RuntimeException e) {
            log.error("Failed to activate store {}", storeId, e);
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.activity.error", null, locale)));
        }
        return "redirect:" + StoresListQuery.safeBack(back);
    }

    private void flashActivationOutcome(StoreActivationService.Outcome outcome, String successKey, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        switch (outcome) {
            case CHANGED -> redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.ok(messageSource.getMessage(successKey, null, locale)));
            case UNCHANGED -> redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.warn(messageSource.getMessage("store.activity.unchanged", null, locale)));
            case MISSING -> redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.activity.missing", null, locale)));
            case TRIAL_ENDED -> redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.activity.activate.trial-ended", null, locale)));
        }
    }

    @PostMapping("/dashboard/stores/cleanup-products")
    public String cleanupProducts(@RequestParam(required = false) String back, Locale locale, RedirectAttributes redirectAttributes) {
        try {
            int deletedCount = orphanedProductCleanupService.cleanupOrphanedProducts();
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.ok(messageSource.getMessage("store.cleanup.success", new Object[]{deletedCount}, locale)));
        } catch (Exception e) {
            log.error("Orphaned product cleanup failed", e);
            redirectAttributes.addFlashAttribute("storesNotice",
                    StoresNotice.bad(messageSource.getMessage("store.cleanup.error", null, locale)));
        }
        return "redirect:" + StoresListQuery.safeBack(back);
    }

}
