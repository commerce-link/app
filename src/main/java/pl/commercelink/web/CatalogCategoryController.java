package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.catalog.InventoryFilterLabels;
import pl.commercelink.web.catalog.MarketplaceDefinitionRow;
import pl.commercelink.web.dtos.CategoryBasicsForm;
import pl.commercelink.web.dtos.CategoryPricingForm;
import pl.commercelink.web.dtos.MarketplaceDefinitionForm;
import pl.commercelink.web.dtos.RecommendationFiltersForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A catalog category: its settings hub, the Basics, Pricing, Marketplaces and Recommendation filters pages and deleting
 * the category. The products of the category live in CatalogProductsController.
 */
@Controller
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CatalogCategoryController {

    private static final String BASICS_VIEW = "catalog/category-basics";
    private static final String BASICS_FRAGMENT = BASICS_VIEW + " :: basicsForm";

    private static final String PRICING_VIEW = "catalog/category-pricing";
    private static final String PRICING_FRAGMENT = PRICING_VIEW + " :: pricingForm";

    private static final String MARKETPLACE_VIEW = "catalog/category-marketplace";
    private static final String MARKETPLACE_FRAGMENT = MARKETPLACE_VIEW + " :: marketplaceForm";

    private static final String FILTERS_VIEW = "catalog/category-filters";
    private static final String FILTERS_FRAGMENT = FILTERS_VIEW + " :: filtersForm";

    /** The ids of the four forms, as the templates give them. */
    private static final String BASICS_FORM = "category-basics-form";
    private static final String PRICING_FORM = "category-pricing-form";
    private static final String MARKETPLACE_FORM = "marketplace-definition-form";
    private static final String FILTERS_FORM = "category-filters-form";

    /** The error message of a broken brand line carries its number, which no message key can hold. */
    private static final String BRAND_LINE_ERROR = "catalog.filter.brandLines.line";

    /** No count of the matching products: the inventory could not answer. */
    private static final int UNKNOWN_MATCH_COUNT = -1;

    /** Outcome of a refused category action, shown by the catalog page in its body; the layout banner is Bulma markup. */
    private static final String ERROR_FLASH = "catalogError";

    /** Said once on the page of a category that was just created, which carries the defaults nobody chose. */
    private static final String NOTICE_FLASH = "categoryNotice";

    /** What a successful save could not refuse but the operator should know; shown beside the saved message. */
    private static final String WARNING_FLASH = "catalogWarning";

    /**
     * The whole catalog is one versioned item, and a save that kept losing the race for it (another section or another
     * category saved at the same moment, over and over) is reported against the form: its id is the error key, so the
     * error summary links to the form, and the operator's values stay in the fields.
     */
    private static final String CONFLICT = "catalog.conflict";

    private final CatalogAccess access;
    private final CategoryDefinitions definitions;
    private final ProductRepository productRepository;
    private final StoresRepository storesRepository;
    private final PimCategoryOptions pimCategoryOptions;
    private final MarketplaceConnections marketplaces;
    private final ProductRecommendationEngine recommendationEngine;
    private final Inventory inventory;
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
        Map<String, String> errors = form.validate(otherNames(catalog, null), null);
        if (!errors.isEmpty()) {
            return rejected(renderBasics(catalog, null, form, errors, model, locale), BASICS_FRAGMENT, async, response);
        }
        CategoryDefinition created;
        try {
            created = definitions.create(catalog, form.toBasics());
        } catch (OptimisticLockingExhaustedException e) {
            return conflict(renderBasics(catalog, null, form, conflictAt(BASICS_FORM), model, locale), BASICS_FRAGMENT, async,
                    response);
        }
        String next = CatalogPaths.category(catalogId, created.getCategoryId());
        String message = messageSource.getMessage("catalog.category.created", new Object[]{created.getName()}, locale);
        // The fresh category has a pricing nobody chose, so its page says so once.
        String notice = messageSource.getMessage("catalog.category.created.defaults", null, locale);
        String warning = emptyListWarning(form, locale);
        if (async) {
            Map<String, String> flash = new LinkedHashMap<>();
            flash.put(SettingsFlash.SAVED_MESSAGE, message);
            flash.put(NOTICE_FLASH, notice);
            if (warning != null) {
                flash.put(WARNING_FLASH, warning);
            }
            SettingsFlash.forNextPage(request, response, next, flash);
            renderBasics(catalog, null, form, Map.of(), model, locale);
            model.addAttribute("redirectTo", next);
            return BASICS_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        redirectAttributes.addFlashAttribute(NOTICE_FLASH, notice);
        if (warning != null) {
            redirectAttributes.addFlashAttribute(WARNING_FLASH, warning);
        }
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
        Map<String, String> errors = form.validate(otherNames(catalog, category), category.getName());
        if (!errors.isEmpty()) {
            return rejected(renderBasics(catalog, category, form, errors, model, locale), BASICS_FRAGMENT, async, response);
        }
        boolean becomesDynamic = category.hasType(CategoryDefinitionType.Managed) && form.isDynamic();
        CategoryDefinitions.Basics basics = form.toBasics();
        try {
            definitions.saveBasics(catalog, category, basics);
        } catch (OptimisticLockingExhaustedException e) {
            return conflict(renderBasics(catalog, category, form, conflictAt(BASICS_FORM), model, locale), BASICS_FRAGMENT, async,
                    response);
        }
        // The service saves a fresh read of the catalog, so the name the category now has is the one just posted.
        String message = messageSource.getMessage("catalog.category.basics.saved",
                new Object[]{StringUtils.trimToNull(basics.name())}, locale);
        if (becomesDynamic) {
            message += " " + messageSource.getMessage("catalog.category.type.changed.dynamic", null, locale);
        }
        return saved(CatalogPaths.categorySettings(catalogId, categoryId), message, emptyListWarning(form, locale), async, model,
                redirectAttributes, request, response, BASICS_FRAGMENT,
                () -> renderBasics(catalog, category, form, Map.of(), model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/pricing")
    public String pricing(@PathVariable String catalogId, @PathVariable String categoryId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        return renderPricing(catalog, category, CategoryPricingForm.from(category), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/pricing")
    public String savePricing(@PathVariable String catalogId, @PathVariable String categoryId, @ModelAttribute CategoryPricingForm form,
                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                              Model model, Locale locale, RedirectAttributes redirectAttributes,
                              HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        form.setRemovedGroups(removedGroups(category, form));
        Map<String, String> errors = form.validate(group -> definitions.productsInPriceGroup(category, group));
        if (!errors.isEmpty()) {
            form.restoreGroupInUse(category.getPriceDefinitions());
            return rejected(renderPricing(catalog, category, form, errors, model, locale), PRICING_FRAGMENT, async, response);
        }
        try {
            definitions.savePricing(catalog, category, form.toPricing());
        } catch (OptimisticLockingExhaustedException e) {
            return conflict(renderPricing(catalog, category, form, conflictAt(PRICING_FORM), model, locale), PRICING_FRAGMENT,
                    async, response);
        }
        return saved(CatalogPaths.categorySettings(catalogId, categoryId),
                messageSource.getMessage("catalog.category.pricing.saved", new Object[]{category.getName()}, locale),
                async, model, redirectAttributes, request, response, PRICING_FRAGMENT,
                () -> renderPricing(catalog, category, form, Map.of(), model, locale));
    }

    /**
     * The page deletes a price group by dropping its fields, so the removal is what the saved category still has and the
     * form no longer carries. Only these groups are looked up in the products, never the ones that came back. A group
     * is a name (NEW-1: one group may be several rules), so it is removed only when no row carries the name any more,
     * and it is looked up once however many of its rules went.
     */
    private static List<String> removedGroups(CategoryDefinition category, CategoryPricingForm form) {
        Set<String> submitted = form.getGroups().stream()
                .map(group -> CategoryPricingForm.groupKey(group.getName())).collect(Collectors.toSet());
        return category.getPriceDefinitions().stream().map(PriceDefinition::getPricingGroup).filter(Objects::nonNull)
                .filter(name -> !submitted.contains(CategoryPricingForm.groupKey(name)))
                .collect(Collectors.toMap(CategoryPricingForm::groupKey, name -> name, (first, other) -> first,
                        LinkedHashMap::new))
                .values().stream().toList();
    }

    private String renderPricing(ProductCatalog catalog, CategoryDefinition category, CategoryPricingForm form,
                                 Map<String, String> errors, Model model, Locale locale) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("formAction", CatalogPaths.categoryPricing(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backHref", CatalogPaths.categorySettings(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.category.settings.title", null, locale));
        model.addAttribute("lead", category.getName() + " · "
                + messageSource.getMessage("catalog.category.pricing.lead", null, locale));
        return PRICING_VIEW;
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/marketplaces")
    public String marketplaces(@PathVariable String catalogId, @PathVariable String categoryId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        Store store = storesRepository.findById(storeId());
        List<Product> products = productRepository.findAll(categoryId);
        List<String> connected = store.getMarketplaces().stream().map(MarketplaceIntegration::getName).toList();
        List<MarketplaceDefinitionRow> rows = connected.stream()
                .map(name -> MarketplaceDefinitionRow.of(catalogId, categoryId, name, marketplaces.displayName(name),
                        category.getCategoryDefinition(name), approved(products, name), true))
                .sorted(Comparator.comparing(MarketplaceDefinitionRow::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        // Definitions left behind by a marketplace the store no longer has, and the nameless ones no export can use.
        List<MarketplaceDefinitionRow> orphans = category.getMarketplaceDefinitions().stream()
                .filter(definition -> definition.getName() == null || !connected.contains(definition.getName()))
                .map(definition -> MarketplaceDefinitionRow.of(catalogId, categoryId, definition.getName(),
                        definition.getName() == null ? null : marketplaces.displayName(definition.getName()),
                        Optional.of(definition), 0, false))
                .toList();
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("rows", rows);
        model.addAttribute("orphans", orphans);
        model.addAttribute("storeMarketplacesHref", SettingsPaths.store(storeId(), "/marketplaces"));
        model.addAttribute("backHref", CatalogPaths.categorySettings(catalogId, categoryId));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.category.settings.title", null, locale));
        model.addAttribute("lead", category.getName() + " · "
                + messageSource.getMessage("catalog.category.marketplaces.lead", null, locale));
        return "catalog/category-marketplaces";
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/marketplaces/{name}")
    public String marketplace(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String name,
                              Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String marketplace = access.requireMarketplace(storesRepository.findById(storeId()), name);
        MarketplaceDefinitionForm form = category.getCategoryDefinition(marketplace).map(MarketplaceDefinitionForm::from)
                .orElseGet(MarketplaceDefinitionForm::empty);
        return renderMarketplace(catalog, category, marketplace, form, Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/marketplaces/{name}")
    public String saveMarketplace(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String name,
                                  @ModelAttribute MarketplaceDefinitionForm form,
                                  @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                  Model model, Locale locale, RedirectAttributes redirectAttributes,
                                  HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String marketplace = access.requireMarketplace(storesRepository.findById(storeId()), name);
        boolean async = SettingsPaths.isAsync(requestedWith);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(renderMarketplace(catalog, category, marketplace, form, errors, model, locale),
                    MARKETPLACE_FRAGMENT, async, response);
        }
        try {
            definitions.saveMarketplace(catalog, category, form.toDefinition(marketplace));
        } catch (OptimisticLockingExhaustedException e) {
            return conflict(renderMarketplace(catalog, category, marketplace, form, conflictAt(MARKETPLACE_FORM), model, locale),
                    MARKETPLACE_FRAGMENT, async, response);
        }
        return saved(CatalogPaths.categoryMarketplaces(catalogId, categoryId),
                messageSource.getMessage("catalog.category.marketplace.saved",
                        new Object[]{marketplaces.displayName(marketplace)}, locale),
                async, model, redirectAttributes, request, response, MARKETPLACE_FRAGMENT,
                () -> renderMarketplace(catalog, category, marketplace, form, Map.of(), model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/marketplaces/{name}/delete")
    public String confirmRemoveMarketplace(@PathVariable String catalogId, @PathVariable String categoryId,
                                           @PathVariable String name, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        // The same guard as the POST: the page never offers to remove a definition the category does not have.
        requireDefinition(category, name);
        String shown = shownName(name, locale);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("catalog.category.marketplace.delete.title", new Object[]{shown}, locale),
                messageSource.getMessage("catalog.category.marketplace.delete.message", null, locale),
                messageSource.getMessage("catalog.category.marketplace.delete", null, locale),
                CatalogPaths.categoryMarketplaceDelete(catalogId, categoryId, name),
                CatalogPaths.categoryMarketplaces(catalogId, categoryId)));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.category.marketplaces.title", null, locale));
        return "settings-confirm";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/marketplaces/{name}/delete")
    public String removeMarketplace(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String name,
                                    Locale locale, RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String target = requireDefinition(category, name);
        try {
            definitions.removeMarketplace(catalog, category, target);
        } catch (OptimisticLockingExhaustedException e) {
            redirectAttributes.addFlashAttribute(ERROR_FLASH, messageSource.getMessage(CONFLICT, null, locale));
            return "redirect:" + CatalogPaths.categoryMarketplaces(catalogId, categoryId);
        }
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("catalog.category.marketplace.deleted",
                new Object[]{shownName(name, locale)}, locale));
        return "redirect:" + CatalogPaths.categoryMarketplaces(catalogId, categoryId);
    }

    /**
     * The name of the category's definition the address points at ({@code null} for the one saved without a name), or a
     * 404 when the category has no such definition. An orphan -- a definition for a marketplace the store no longer
     * has -- is found here on purpose: removing it is what its row offers.
     */
    private static String requireDefinition(CategoryDefinition category, String name) {
        String target = MarketplaceDefinitionRow.UNNAMED.equals(name) ? null : name;
        if (category.getMarketplaceDefinitions().stream().noneMatch(definition -> Objects.equals(definition.getName(), target))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return target;
    }

    /** A definition saved without a name has none to show, so it is named like any other untitled record. */
    private String shownName(String name, Locale locale) {
        return MarketplaceDefinitionRow.UNNAMED.equals(name)
                ? messageSource.getMessage("settings.list.untitled", null, locale) : marketplaces.displayName(name);
    }

    private String renderMarketplace(ProductCatalog catalog, CategoryDefinition category, String marketplace,
                                     MarketplaceDefinitionForm form, Map<String, String> errors, Model model, Locale locale) {
        String displayName = marketplaces.displayName(marketplace);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("marketplaceName", displayName);
        model.addAttribute("approvedProducts", approved(productRepository.findAll(category.getCategoryId()), marketplace));
        model.addAttribute("formAction",
                CatalogPaths.categoryMarketplace(catalog.getCatalogId(), category.getCategoryId(), marketplace));
        model.addAttribute("backHref", CatalogPaths.categoryMarketplaces(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.category.marketplaces.title", null, locale));
        model.addAttribute("lead", category.getName() + " · " + messageSource.getMessage(
                "catalog.category.marketplace.lead", new Object[]{displayName}, locale));
        return MARKETPLACE_VIEW;
    }

    private static int approved(List<Product> products, String marketplace) {
        return (int) products.stream().filter(product -> product.isApprovedForMarketplace(marketplace)).count();
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/filters")
    public String filters(@PathVariable String catalogId, @PathVariable String categoryId, Model model, Locale locale) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        return renderFilters(catalog, category, RecommendationFiltersForm.from(category), Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/settings/filters")
    public String saveFilters(@PathVariable String catalogId, @PathVariable String categoryId,
                              @ModelAttribute RecommendationFiltersForm form,
                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                              Model model, Locale locale, RedirectAttributes redirectAttributes,
                              HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(renderFilters(catalog, category, form, errors, model, locale), FILTERS_FRAGMENT, async, response);
        }
        try {
            definitions.saveFilters(catalog, category, form.toDefinitions());
        } catch (OptimisticLockingExhaustedException e) {
            return conflict(renderFilters(catalog, category, form, conflictAt(FILTERS_FORM), model, locale), FILTERS_FRAGMENT,
                    async, response);
        }
        return saved(CatalogPaths.categorySettings(catalogId, categoryId),
                messageSource.getMessage("catalog.category.filters.saved", new Object[]{category.getName()}, locale),
                async, model, redirectAttributes, request, response, FILTERS_FRAGMENT,
                () -> renderFilters(catalog, category, form, Map.of(), model, locale));
    }

    private String renderFilters(ProductCatalog catalog, CategoryDefinition category, RecommendationFiltersForm form,
                                 Map<String, String> errors, Model model, Locale locale) {
        Map<String, String> texts = translated(errors, locale);
        model.addAttribute("form", form);
        model.addAttribute("errors", texts);
        model.addAttribute("errorSummary", RecommendationFiltersForm.summary(texts, (number, text) ->
                messageSource.getMessage(RecommendationFiltersForm.SUMMARY_LINE, new Object[]{number, text}, locale)));
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("filterTypes", InventoryFilterLabels.options());
        model.addAttribute("listVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.LIST));
        model.addAttribute("brandVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.BY_BRAND));
        model.addAttribute("formAction", CatalogPaths.categoryFilters(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backHref", CatalogPaths.categorySettings(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.category.settings.title", null, locale));
        // The lead is a block of the template: the name as text and, when known, the count linked to adding products.
        int matching = matchingProducts(category);
        if (matching != UNKNOWN_MATCH_COUNT) {
            model.addAttribute("matchingProducts", matching);
        }
        model.addAttribute("productsAddHref", CatalogPaths.productsAdd(catalog.getCatalogId(), category.getCategoryId()));
        return FILTERS_VIEW;
    }

    /** The error of a line of the brand lines names the line, so the page is given the texts instead of the keys. */
    private Map<String, String> translated(Map<String, String> errors, Locale locale) {
        Map<String, String> texts = new LinkedHashMap<>();
        errors.forEach((field, key) -> texts.put(field, key.startsWith(BRAND_LINE_ERROR + ":")
                ? messageSource.getMessage(BRAND_LINE_ERROR, new Object[]{key.substring(key.indexOf(':') + 1)}, locale)
                : messageSource.getMessage(key, null, locale)));
        return texts;
    }

    /**
     * The count is a pass over the inventory on every render of the page, the failed saves included; the page is rare
     * enough for that. Nothing of the page depends on the count, so an inventory that cannot answer only costs the line.
     */
    private int matchingProducts(CategoryDefinition category) {
        if (!category.hasCategoryMapping()) {
            return 0;
        }
        try {
            return recommendationEngine.getRecommendations(category, inventory.withEnabledSuppliersOnly(storeId())).size();
        } catch (RuntimeException e) {
            log.warn("Cannot count the products matching the filters of category {}", category.getCategoryId(), e);
            return UNKNOWN_MATCH_COUNT;
        }
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/delete")
    public String confirmDelete(@PathVariable String catalogId, @PathVariable String categoryId, Model model, Locale locale,
                                RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        // The same refusal as the POST (ruling 5a): a protected category gets no page with a button that cannot work.
        if (category.isDeletionProtection()) {
            return refuseDeletion(catalogId, category, locale, redirectAttributes);
        }
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
            return refuseDeletion(catalogId, category, locale, redirectAttributes);
        }
        try {
            definitions.remove(catalog, category);
        } catch (IllegalStateException e) {
            // The protection was switched on between the check above and the save, which checks it again on its read.
            return refuseDeletion(catalogId, category, locale, redirectAttributes);
        } catch (OptimisticLockingExhaustedException e) {
            redirectAttributes.addFlashAttribute(ERROR_FLASH, messageSource.getMessage(CONFLICT, null, locale));
            return "redirect:" + CatalogPaths.catalog(catalogId);
        }
        SettingsFlash.onRedirect(redirectAttributes,
                messageSource.getMessage("catalog.category.deleted", new Object[]{category.getName()}, locale));
        return "redirect:" + CatalogPaths.catalog(catalogId);
    }

    private String refuseDeletion(String catalogId, CategoryDefinition category, Locale locale,
                                  RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(ERROR_FLASH,
                messageSource.getMessage("catalog.category.delete.protected", new Object[]{category.getName()}, locale));
        return "redirect:" + CatalogPaths.catalog(catalogId);
    }

    /** What the deletion takes with it: nothing for a computed list, otherwise whatever the service says it will remove. */
    private String deletionMessage(ProductCatalog catalog, CategoryDefinition category, Locale locale) {
        if (category.hasType(CategoryDefinitionType.Dynamic)) {
            return messageSource.getMessage("catalog.category.delete.message.dynamic", null, locale);
        }
        CategoryDefinitions.DeletionPreview preview = definitions.deletionPreview(catalog, category);
        return preview.productsKept()
                ? messageSource.getMessage("catalog.category.delete.message.kept", null, locale)
                : messageSource.getMessage("catalog.category.delete.message",
                        new Object[]{preview.productsToDelete()}, locale);
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
        model.addAttribute("selectedCategoryOptions", pimCategoryOptions.selectedOf(form.getPimCategoryIds()));
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
                ? existing.getName() + " · " + messageSource.getMessage("catalog.category.basics.lead", null, locale)
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

    /** The error of a save that kept losing the race, keyed by the id of its form. */
    private static Map<String, String> conflictAt(String formId) {
        return Map.of(formId, CONFLICT);
    }

    /** A conflict is a refused save like any other (422), with or without JavaScript: nothing was written. */
    private String conflict(String view, String fragment, boolean async, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        return async ? fragment : view;
    }

    /** Success: with JavaScript the form answers 200 + data-cl-redirect and the script navigates; otherwise a PRG redirect. */
    private String saved(String nextPath, String message, boolean async, Model model, RedirectAttributes redirectAttributes,
                         HttpServletRequest request, HttpServletResponse response, String fragment, Supplier<String> rerender) {
        return saved(nextPath, message, null, async, model, redirectAttributes, request, response, fragment, rerender);
    }

    /** The same with something the save could not refuse: the warning travels to the page the save returns to. */
    private String saved(String nextPath, String message, String warning, boolean async, Model model,
                         RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response,
                         String fragment, Supplier<String> rerender) {
        if (async) {
            Map<String, String> flash = new LinkedHashMap<>();
            flash.put(SettingsFlash.SAVED_MESSAGE, message);
            if (warning != null) {
                flash.put(WARNING_FLASH, warning);
            }
            SettingsFlash.forNextPage(request, response, nextPath, flash);
            rerender.get();
            model.addAttribute("redirectTo", nextPath);
            return fragment;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        if (warning != null) {
            redirectAttributes.addFlashAttribute(WARNING_FLASH, warning);
        }
        return "redirect:" + nextPath;
    }

    /**
     * What the saved basics say about the list of products the category will have. Only an automatic category is
     * checked: a manual one is given its products by hand, so neither the mapping nor the inventory decides its list.
     * The form is read rather than the saved category: it carries exactly what the save has just written.
     */
    private String emptyListWarning(CategoryBasicsForm form, Locale locale) {
        if (!form.isDynamic()) {
            return null;
        }
        List<String> mapped = form.getPimCategoryIds().stream().filter(StringUtils::isNotBlank).toList();
        if (mapped.isEmpty()) {
            return messageSource.getMessage("catalog.category.noMapping", null, locale);
        }
        List<String> empty = withoutInventory(mapped);
        if (empty.isEmpty()) {
            return null;
        }
        return messageSource.getMessage("catalog.category.emptyInventory",
                new Object[]{String.join(", ", pimCategoryOptions.namesOf(empty))}, locale);
    }

    /**
     * Which of the mapped PIM categories no enabled supplier offers anything in. The answer costs a pass over the
     * inventory, so it is asked only for a non-empty mapping; an inventory that cannot answer only costs the warning.
     */
    private List<String> withoutInventory(List<String> pimCategoryIds) {
        try {
            Map<String, Collection<MatchedInventory>> matches =
                    inventory.withEnabledSuppliersOnly(storeId()).findAllByProductCategoryIds(pimCategoryIds);
            return pimCategoryIds.stream()
                    .filter(id -> matches.getOrDefault(id, List.of()).stream().noneMatch(MatchedInventory::hasAnyOffers))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Cannot check the inventory of the PIM categories {}", pimCategoryIds, e);
            return List.of();
        }
    }

    private static String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
