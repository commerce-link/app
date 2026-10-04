package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.MarginSettingsForm;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Settings › Margins: what the store counts as a low margin, which marks order items on the order page (ItemMargin).
 * A default percent and percents for chosen categories, offered from the store's catalogs. The store comes from the
 * session (ADMIN) or the path (SUPER_ADMIN), never from the form.
 */
@Controller
@RequiredArgsConstructor
public class StoreMarginsSettingsController {

    private static final String VIEW = "store-margins";
    private static final String FORM_FRAGMENT = VIEW + " :: marginsForm";
    private static final String PATH = "/margins";

    private final StoresRepository storesRepository;
    private final StoreCategories storeCategories;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/margins")
    @PreAuthorize("hasRole('ADMIN')")
    public String margins(Model model, Locale locale) {
        Store store = requireStore(CustomSecurityContext.getStoreId());
        return render(store, MarginSettingsForm.from(store.getMarginConfiguration()), Map.of(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/margins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMargins(@PathVariable String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        return render(store, MarginSettingsForm.from(store.getMarginConfiguration()), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/store/margins")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute MarginSettingsForm form,
                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                       Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/margins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSave(@PathVariable String storeId, @ModelAttribute MarginSettingsForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    private String save(String storeId, MarginSettingsForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            String view = render(store, form, errors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }
        store.setMarginConfiguration(form.toConfiguration());
        storesRepository.save(store);

        String message = messageSource.getMessage("store.margins.update.success", null, locale);
        if (async) {
            render(store, MarginSettingsForm.from(store.getMarginConfiguration()), Map.of(), model, locale);
            model.addAttribute("savedMessage", message);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + SettingsPaths.store(storeId, PATH);
    }

    private String render(Store store, MarginSettingsForm form, Map<String, String> errors, Model model, Locale locale) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", form.errorLabels(
                number -> messageSource.getMessage("store.margins.category.field", new Object[]{number}, locale),
                number -> messageSource.getMessage("store.margins.percent.field", new Object[]{number}, locale)));
        model.addAttribute("categoryOptions", categoryOptions(store.getStoreId(), form));
        model.addAttribute("formAction", SettingsPaths.store(store.getStoreId(), PATH));
        return VIEW;
    }

    /**
     * The picker's options: the categories of the store's catalogs, then any category of the form they no longer have
     * (a saved threshold whose category was renamed), so the picker offers what the row shows. A flat list: the
     * picker's breadcrumbs stay empty without parents.
     */
    private List<PimCategoryOptions.CategoryOption> categoryOptions(String storeId, MarginSettingsForm form) {
        Set<String> names = new LinkedHashSet<>(storeCategories.namesFor(storeId));
        form.getCategories().stream().map(MarginSettingsForm.CategoryRow::getCategory)
                .map(StringUtils::trimToNull).filter(Objects::nonNull).forEach(names::add);
        return names.stream().map(name -> new PimCategoryOptions.CategoryOption(name, name, null)).toList();
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
