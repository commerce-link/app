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
import org.springframework.web.util.HtmlUtils;
import pl.commercelink.inventory.Inventory;
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

    /** The error message of a broken brand line carries its number, which no message key can hold. */
    private static final String BRAND_LINE_ERROR = "catalog.filter.brandLines.line";

    /** No count of the matching products: the inventory could not answer. */
    private static final int UNKNOWN_MATCH_COUNT = -1;

    /** Outcome of a refused category action, shown by the catalog page in its body; the layout banner is Bulma markup. */
    private static final String ERROR_FLASH = "catalogError";

    /** Said once on the page of a category that was just created, which carries the defaults nobody chose. */
    private static final String NOTICE_FLASH = "categoryNotice";

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
            return rejected(renderPricing(catalog, category, form, errors, model, locale), PRICING_FRAGMENT, async, response);
        }
        definitions.savePricing(catalog, category, form.toStock(), form.toAvailability(), form.toGroups());
        return saved(CatalogPaths.categorySettings(catalogId, categoryId),
                messageSource.getMessage("catalog.category.pricing.saved", new Object[]{category.getName()}, locale),
                async, model, redirectAttributes, request, response, PRICING_FRAGMENT,
                () -> renderPricing(catalog, category, form, Map.of(), model, locale));
    }

    /**
     * The page deletes a price group by dropping its fields, so the removal is what the saved category still has and the
     * form no longer carries. Only these groups are looked up in the products, never the ones that came back.
     */
    private static List<String> removedGroups(CategoryDefinition category, CategoryPricingForm form) {
        Set<String> submitted = form.getGroups().stream()
                .map(group -> StringUtils.defaultString(group.getName()).trim().toLowerCase()).collect(Collectors.toSet());
        return category.getPriceDefinitions().stream().map(PriceDefinition::getPricingGroup).filter(Objects::nonNull)
                .filter(name -> !submitted.contains(name.trim().toLowerCase())).toList();
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
        model.addAttribute("lead", HtmlUtils.htmlEscape(category.getName()) + " · "
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
        model.addAttribute("lead", HtmlUtils.htmlEscape(category.getName()) + " · "
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
        definitions.saveMarketplace(catalog, category, form.toDefinition(marketplace));
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
        String target = MarketplaceDefinitionRow.UNNAMED.equals(name) ? null : name;
        if (category.getMarketplaceDefinitions().stream().noneMatch(definition -> Objects.equals(definition.getName(), target))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        definitions.removeMarketplace(catalog, category, target);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("catalog.category.marketplace.deleted",
                new Object[]{shownName(name, locale)}, locale));
        return "redirect:" + CatalogPaths.categoryMarketplaces(catalogId, categoryId);
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
        model.addAttribute("lead", HtmlUtils.htmlEscape(category.getName()) + " · " + messageSource.getMessage(
                "catalog.category.marketplace.lead", new Object[]{HtmlUtils.htmlEscape(displayName)}, locale));
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
        definitions.saveFilters(catalog, category, form.toDefinitions());
        return saved(CatalogPaths.categorySettings(catalogId, categoryId),
                messageSource.getMessage("catalog.category.filters.saved", new Object[]{category.getName()}, locale),
                async, model, redirectAttributes, request, response, FILTERS_FRAGMENT,
                () -> renderFilters(catalog, category, form, Map.of(), model, locale));
    }

    private String renderFilters(ProductCatalog catalog, CategoryDefinition category, RecommendationFiltersForm form,
                                 Map<String, String> errors, Model model, Locale locale) {
        model.addAttribute("form", form);
        model.addAttribute("errors", translated(errors, locale));
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("filterTypes", InventoryFilterLabels.options());
        model.addAttribute("listVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.LIST));
        model.addAttribute("brandVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.BY_BRAND));
        model.addAttribute("formAction", CatalogPaths.categoryFilters(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backHref", CatalogPaths.categorySettings(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backLabel", messageSource.getMessage("catalog.category.settings.title", null, locale));
        model.addAttribute("lead", filtersLead(catalog, category, locale));
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

    /** How many products the filters let through today, linked to the page that adds them; left out when unknown. */
    private String filtersLead(ProductCatalog catalog, CategoryDefinition category, Locale locale) {
        String lead = HtmlUtils.htmlEscape(category.getName()) + " · "
                + messageSource.getMessage("catalog.category.filters.lead", null, locale);
        int matching = matchingProducts(category);
        if (matching == UNKNOWN_MATCH_COUNT) {
            return lead;
        }
        return lead + " <a href=\"" + CatalogPaths.productsAdd(catalog.getCatalogId(), category.getCategoryId()) + "\">"
                + HtmlUtils.htmlEscape(messageSource.getMessage("catalog.category.filters.matching", new Object[]{matching}, locale))
                + "</a>";
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
