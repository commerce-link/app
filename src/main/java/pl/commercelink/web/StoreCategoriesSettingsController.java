package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PimCategoryOptions.TopLevelChoice;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Settings › Categories. The ticked top levels narrow the category pickers of the product catalogs; the store comes
 * from the session (ADMIN) or the path (SUPER_ADMIN), never from the form.
 */
@Controller
@RequiredArgsConstructor
public class StoreCategoriesSettingsController {

    private static final String VIEW = "store-categories";
    private static final String FORM_FRAGMENT = VIEW + " :: categoriesForm";

    private final StoresRepository storesRepository;
    private final PimCategoryOptions pimCategoryOptions;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public String categories(Model model) {
        return render(CustomSecurityContext.getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCategories(@PathVariable String storeId, Model model) {
        return render(storeId, model);
    }

    @PostMapping("/dashboard/store/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveCategories(@RequestParam(required = false) List<String> enabledCategories,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return save(CustomSecurityContext.getStoreId(), enabledCategories, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveCategories(@PathVariable String storeId,
                                           @RequestParam(required = false) List<String> enabledCategories,
                                           @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                           Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return save(storeId, enabledCategories, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes);
    }

    private String save(String storeId, List<String> submitted, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return render(storeId, model);
        }

        apply(store, submitted);
        storesRepository.save(store);

        String successMessage = messageSource.getMessage("store.categories.update.success", null, locale);
        if (async) {
            render(storeId, model);
            model.addAttribute("savedMessage", successMessage);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, successMessage);
        return "redirect:" + SettingsPaths.store(storeId, "/categories");
    }

    /**
     * Only names the page actually offered are stored: a name PIM does not know and the store does not already have
     * cannot arrive from the form, so a submission carrying one is ignored rather than written to the store.
     */
    private void apply(Store store, List<String> submitted) {
        Set<String> offered = pimCategoryOptions.topLevelChoices(store.getEnabledCategories()).stream()
                .map(TopLevelChoice::name)
                .collect(Collectors.toSet());
        List<String> enabled = submitted == null
                ? List.of()
                : submitted.stream().filter(offered::contains).distinct().toList();
        if (store.getFulfilmentConfiguration() == null) {
            store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        }
        store.getFulfilmentConfiguration().setEnabledCategories(new ArrayList<>(enabled));
    }

    private String render(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }
        List<TopLevelChoice> choices = pimCategoryOptions.topLevelChoices(store.getEnabledCategories());
        model.addAttribute("choices", choices);
        model.addAttribute("selectedCount", choices.stream().filter(TopLevelChoice::selected).count());
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/categories"));
        return VIEW;
    }
}
