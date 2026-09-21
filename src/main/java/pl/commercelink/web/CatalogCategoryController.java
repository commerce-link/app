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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.HtmlUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.dtos.CategoryBasicsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A catalog category: its settings hub, the Basics page and deleting the category. The products of the category live
 * in CatalogProductsController.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CatalogCategoryController {

    private static final String BASICS_VIEW = "catalog/category-basics";
    private static final String BASICS_FRAGMENT = BASICS_VIEW + " :: basicsForm";

    /** Outcome of a refused category action, shown by the catalog page in its body; the layout banner is Bulma markup. */
    private static final String ERROR_FLASH = "catalogError";

    /** Said once on the page of a category that was just created, which carries the defaults nobody chose. */
    private static final String NOTICE_FLASH = "categoryNotice";

    private final CatalogAccess access;
    private final CategoryDefinitions definitions;
    private final ProductRepository productRepository;
    private final StoresRepository storesRepository;
    private final PimCategoryOptions pimCategoryOptions;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings")
    public String settings(@PathVariable String catalogId, @PathVariable String categoryId, Model model) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("backHref", CatalogPaths.category(catalogId, categoryId));
        model.addAttribute("basicsHref", CatalogPaths.categoryBasics(catalogId, categoryId));
        model.addAttribute("pricingHref", CatalogPaths.categoryPricing(catalogId, categoryId));
        model.addAttribute("marketplacesHref", CatalogPaths.categoryMarketplaces(catalogId, categoryId));
        model.addAttribute("filtersHref", CatalogPaths.categoryFilters(catalogId, categoryId));
        return "catalog/category-settings";
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/new")
    public String newCategory(@PathVariable String catalogId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        return renderBasics(catalog, null, CategoryBasicsForm.forNewCategory(), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/new")
    public String createCategory(@PathVariable String catalogId, @ModelAttribute CategoryBasicsForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        Map<String, String> errors = form.validate(otherNames(catalog, null));
        if (!errors.isEmpty()) {
            return rejected(renderBasics(catalog, null, form, errors, model, locale), BASICS_FRAGMENT, async, response);
        }
        CategoryDefinition created = definitions.create(catalog, form.toBasics());
        String next = CatalogPaths.category(catalogId, created.getCategoryId());
        String message = messageSource.getMessage("catalog.category.created", new Object[]{created.getName()}, locale);
        // The fresh category has a pricing nobody chose, so its page says so once.
        String notice = messageSource.getMessage("catalog.category.created.defaults", null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, next, Map.of(SettingsFlash.SAVED_MESSAGE, message, NOTICE_FLASH, notice));
            renderBasics(catalog, null, form, Map.of(), model, locale);
            model.addAttribute("redirectTo", next);
            return BASICS_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        redirectAttributes.addFlashAttribute(NOTICE_FLASH, notice);
        return "redirect:" + next;
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/basics")
    public String basics(@PathVariable String catalogId, @PathVariable String categoryId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        return renderBasics(catalog, category, CategoryBasicsForm.from(category), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/basics")
    public String saveBasics(@PathVariable String catalogId, @PathVariable String categoryId, @ModelAttribute CategoryBasicsForm form,
                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes,
                             HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        Map<String, String> errors = form.validate(otherNames(catalog, category));
        if (!errors.isEmpty()) {
            return rejected(renderBasics(catalog, category, form, errors, model, locale), BASICS_FRAGMENT, async, response);
        }
        boolean becomesDynamic = category.hasType(CategoryDefinitionType.Managed) && form.isDynamic();
        definitions.saveBasics(catalog, category, form.toBasics());
        String message = messageSource.getMessage("catalog.category.basics.saved", new Object[]{category.getName()}, locale);
        if (becomesDynamic) {
            message += " " + messageSource.getMessage("catalog.category.type.changed.dynamic", null, locale);
        }
        return saved(CatalogPaths.categorySettings(catalogId, categoryId), message, async, model, redirectAttributes, request,
                response, BASICS_FRAGMENT, () -> renderBasics(catalog, category, form, Map.of(), model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/delete")
    public String confirmDelete(@PathVariable String catalogId, @PathVariable String categoryId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("catalog.category.delete.title", new Object[]{category.getName()}, locale),
                deletionMessage(catalog, category, locale),
                messageSource.getMessage("catalog.category.delete", null, locale),
                CatalogPaths.categoryDelete(catalogId, categoryId), CatalogPaths.catalog(catalogId)));
        model.addAttribute("backLabel", catalog.getName());
        return "settings-confirm";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/delete")
    public String delete(@PathVariable String catalogId, @PathVariable String categoryId, Locale locale,
                         RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        if (category.isDeletionProtection()) {
            redirectAttributes.addFlashAttribute(ERROR_FLASH,
                    messageSource.getMessage("catalog.category.delete.protected", new Object[]{category.getName()}, locale));
            return "redirect:" + CatalogPaths.catalog(catalogId);
        }
        definitions.remove(catalog, category);
        SettingsFlash.onRedirect(redirectAttributes,
                messageSource.getMessage("catalog.category.deleted", new Object[]{category.getName()}, locale));
        return "redirect:" + CatalogPaths.catalog(catalogId);
    }

    /** What the deletion takes with it: nothing for a computed list, nothing when a twin category keeps the products. */
    private String deletionMessage(ProductCatalog catalog, CategoryDefinition category, Locale locale) {
        if (category.hasType(CategoryDefinitionType.Dynamic)) {
            return messageSource.getMessage("catalog.category.delete.message.dynamic", null, locale);
        }
        boolean kept = category.hasCategoryMapping() && catalog.getCategories().stream()
                .filter(other -> other != category)
                .anyMatch(other -> other.getPimCategoryIds().stream().anyMatch(category.getPimCategoryIds()::contains));
        return kept
                ? messageSource.getMessage("catalog.category.delete.message.kept", null, locale)
                : messageSource.getMessage("catalog.category.delete.message",
                        new Object[]{productRepository.findAll(category.getCategoryId()).size()}, locale);
    }

    private String renderBasics(ProductCatalog catalog, CategoryDefinition existing, CategoryBasicsForm form,
                                Map<String, String> errors, Model model, Locale locale) {
        Store store = storesRepository.findById(storeId());
        List<PimCategoryOptions.CategoryOption> options =
                pimCategoryOptions.leafOptionsUnder(store.getEnabledCategories(), form.getPimCategoryIds());
        boolean edit = existing != null;
        String catalogId = catalog.getCatalogId();
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("existing", edit);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", existing);
        model.addAttribute("categoryOptions", options);
        model.addAttribute("categoryAncestors",
                pimCategoryOptions.ancestorsOf(options.stream().map(PimCategoryOptions.CategoryOption::id).toList()));
        model.addAttribute("selectedCategoryOptions", pimCategoryOptions.optionsOf(form.getPimCategoryIds()));
        model.addAttribute("types", Arrays.stream(CategoryDefinitionType.values())
                .map(type -> Map.of("value", type.name(), "labelKey", CategoryTypeLabels.labelKey(type),
                        "descKey", CategoryTypeLabels.descriptionKey(type)))
                .toList());
        model.addAttribute("formAction", edit
                ? CatalogPaths.categoryBasics(catalogId, existing.getCategoryId()) : CatalogPaths.newCategory(catalogId));
        model.addAttribute("backHref", edit
                ? CatalogPaths.categorySettings(catalogId, existing.getCategoryId()) : CatalogPaths.catalog(catalogId));
        model.addAttribute("backLabel", edit
                ? messageSource.getMessage("catalog.category.settings.title", null, locale) : catalog.getName());
        model.addAttribute("pageTitle",
                messageSource.getMessage(edit ? "catalog.category.basics.title" : "catalog.category.new.title", null, locale));
        model.addAttribute("lead", edit
                ? HtmlUtils.htmlEscape(existing.getName()) + " · " + messageSource.getMessage("catalog.category.basics.lead", null, locale)
                : messageSource.getMessage("catalog.category.new.lead", null, locale));
        // An automatic category has no rows in the products table: its list is computed from the inventory.
        List<Product> products = edit && existing.hasType(CategoryDefinitionType.Managed)
                ? productRepository.findAll(existing.getCategoryId()) : List.of();
        model.addAttribute("managedProductsCount", products.size());
        model.addAttribute("labelsOutsideCount", edit && existing.hasGrouping()
                ? products.stream().filter(product -> !existing.getGroupingOrder().contains(product.getLabel())).count() : 0L);
        model.addAttribute("deleteHref", edit && !existing.isDeletionProtection()
                ? CatalogPaths.categoryDelete(catalogId, existing.getCategoryId()) : null);
        return BASICS_VIEW;
    }

    private Set<String> otherNames(ProductCatalog catalog, CategoryDefinition except) {
        return catalog.getCategories().stream().filter(category -> category != except)
                .map(CategoryDefinition::getName).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private String rejected(String view, String fragment, boolean async, HttpServletResponse response) {
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return fragment;
        }
        return view;
    }

    /** Success: with JavaScript the form answers 200 + data-cl-redirect and the script navigates; otherwise a PRG redirect. */
    private String saved(String nextPath, String message, boolean async, Model model, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response, String fragment, Supplier<String> rerender) {
        if (async) {
            SettingsFlash.forNextPage(request, response, nextPath, message);
            rerender.get();
            model.addAttribute("redirectTo", nextPath);
            return fragment;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + nextPath;
    }

    private static String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
