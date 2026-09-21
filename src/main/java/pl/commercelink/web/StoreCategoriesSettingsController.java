package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
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
    public String saveCategories(HttpServletRequest request,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), submitted(request), SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveCategories(@PathVariable String storeId, HttpServletRequest request,
                                           @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                           Model model, Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletResponse response) {
        return save(storeId, submitted(request), SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                response);
    }

    /**
     * The ticked names as sent. Not {@code @RequestParam List<String>}: Spring splits a single value on commas, and
     * three PIM top levels have a comma in their name ("Żywność, napoje i tytoń").
     */
    private static List<String> submitted(HttpServletRequest request) {
        String[] values = request.getParameterValues("enabledCategories");
        return values == null ? List.of() : List.of(values);
    }

    private String save(String storeId, List<String> submitted, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = requireStore(storeId);
        List<TopLevelChoice> choices = pimCategoryOptions.topLevelChoices(store.getEnabledCategories());
        // Without the catalogue the page cannot tell what it offers, so it saves nothing rather than drop the selection.
        if (!catalogueAvailable(choices)) {
            if (async) {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                return render(storeId, model);
            }
            return "redirect:" + SettingsPaths.store(storeId, "/categories");
        }

        apply(store, choices, submitted);
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
    private static void apply(Store store, List<TopLevelChoice> choices, List<String> submitted) {
        Set<String> offered = choices.stream()
                .map(TopLevelChoice::name)
                .collect(Collectors.toSet());
        List<String> enabled = submitted.stream().filter(offered::contains).distinct().toList();
        if (store.getFulfilmentConfiguration() == null) {
            store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        }
        store.getFulfilmentConfiguration().setEnabledCategories(new ArrayList<>(enabled));
    }

    private String render(String storeId, Model model) {
        Store store = requireStore(storeId);
        List<TopLevelChoice> choices = pimCategoryOptions.topLevelChoices(store.getEnabledCategories());
        model.addAttribute("catalogueAvailable", catalogueAvailable(choices));
        model.addAttribute("choices", choices);
        model.addAttribute("selectedCount", choices.stream().filter(TopLevelChoice::selected).count());
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/categories"));
        return VIEW;
    }

    /** An empty PIM cache (start-up, failed load) would otherwise show every saved category as outside the catalogue. */
    private static boolean catalogueAvailable(List<TopLevelChoice> choices) {
        return choices.stream().anyMatch(TopLevelChoice::inCatalogue);
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
