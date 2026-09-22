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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.catalog.CategoryPageModel;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.catalog.ProductRow;
import pl.commercelink.web.catalog.ProductStatus;
import pl.commercelink.web.catalog.RecommendationRow;
import pl.commercelink.web.dtos.ProductsBulkAddForm;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Products of a catalog category: the category page (the products table with its filters), the bulk actions on it and
 * adding products from the inventory (proposals, the review of their data, the save).
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CatalogProductsController {

    private static final String ALL = "all";

    /**
     * The label group is declared so its value travels in the address, but never filled in here: the default is a
     * space-separated list of {@code group:value} pairs and a label may contain a space ("RTX 5080 Ti").
     */
    private static final String LABEL_DEFAULT = " label:" + ALL;

    private final CatalogAccess access;
    private final ProductRepository productRepository;
    private final ProductRecommendationEngine recommendationEngine;
    private final Inventory inventory;
    private final MarketplaceConnections marketplaces;
    private final PimCategoryOptions pimCategoryOptions;
    private final SupplierLabels supplierLabels;
    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}")
    public String category(@PathVariable String catalogId, @PathVariable String categoryId,
                           @RequestParam(required = false, defaultValue = "active") String status,
                           @RequestParam(required = false, defaultValue = ALL) String feature,
                           Model model) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        CategoryPageModel page = CategoryPageModel.of(rowsOf(catalog, category), category);
        String startStatus = statusFilter(status);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("page", page);
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("features", CategoryPageModel.FEATURES);
        model.addAttribute("filterDefault", "status:" + startStatus
                + " feature:" + (CategoryPageModel.FEATURES.contains(feature) ? feature : ALL) + LABEL_DEFAULT);
        model.addAttribute("statusFilter", startStatus);
        model.addAttribute("typeLabelKey", CategoryTypeLabels.labelKey(category.getType()));
        model.addAttribute("typeTone", CategoryTypeLabels.tone(category.getType()));
        model.addAttribute("pimNames", String.join(", ", pimCategoryOptions.namesOf(category.getPimCategoryIds())));
        model.addAttribute("categoryMarketplaceNames", category.getMarketplaceDefinitions().stream()
                .filter(definition -> definition.isEnabled() && definition.isComplete())
                .map(MarketplaceDefinition::getName)
                .map(marketplaces::displayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
        model.addAttribute("settingsHref", CatalogPaths.categorySettings(catalogId, categoryId));
        model.addAttribute("basicsHref", CatalogPaths.categoryBasics(catalogId, categoryId));
        model.addAttribute("addHref", CatalogPaths.productsAdd(catalogId, categoryId));
        model.addAttribute("newProductHref", CatalogPaths.newProduct(catalogId, categoryId));
        model.addAttribute("bulkAction", CatalogPaths.productsBulk(catalogId, categoryId));
        model.addAttribute("backHref", CatalogPaths.catalog(catalogId));
        return "catalog/category";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/bulk")
    public String bulk(@PathVariable String catalogId, @PathVariable String categoryId,
                       @RequestParam String action, @RequestParam(required = false) List<String> productIds,
                       @RequestParam(required = false, defaultValue = "active") String status,
                       Locale locale, RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String back = "redirect:" + CatalogPaths.category(catalogId, categoryId) + "?status=" + statusFilter(status);
        if (productIds == null || productIds.isEmpty()) {
            redirectAttributes.addFlashAttribute(CatalogsController.ERROR_FLASH,
                    messageSource.getMessage("catalog.products.bulk.none", null, locale));
            return back;
        }
        // Read by the category's own key, so an id smuggled in from another category simply finds nothing.
        List<Product> products = productIds.stream()
                .map(productId -> productRepository.findByProductId(category.getCategoryId(), productId))
                .filter(Objects::nonNull)
                .toList();
        String messageKey = switch (action) {
            case "enable" -> save(products, true);
            case "disable" -> save(products, false);
            case "delete" -> delete(products);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        SettingsFlash.onRedirect(redirectAttributes,
                messageSource.getMessage(messageKey, new Object[]{products.size()}, locale));
        return back;
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/add")
    public String addProducts(@PathVariable String catalogId, @PathVariable String categoryId, Model model) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        // Without PIM categories the engine has nothing to match, and reading the inventory would be wasted work.
        List<RecommendationRow> rows = List.of();
        if (category.hasCategoryMapping()) {
            SupplierLabelMap labels = supplierLabels.forStoreId(storeId());
            rows = recommendationEngine.getRecommendations(category, inventory.withEnabledSuppliersOnly(storeId())).stream()
                    .map(recommendation -> RecommendationRow.of(recommendation, catalogId, categoryId, labels::of))
                    .toList();
        }
        Map<String, Long> brandCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        rows.forEach(row -> brandCounts.merge(row.brand(), 1L, Long::sum));
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("rows", rows);
        model.addAttribute("brandCounts", brandCounts);
        model.addAttribute("hasMapping", category.hasCategoryMapping());
        model.addAttribute("pimNames", String.join(", ", pimCategoryOptions.namesOf(category.getPimCategoryIds())));
        model.addAttribute("filtersCount", category.getInventoryDefinitions().size());
        model.addAttribute("filtersHref", CatalogPaths.categoryFilters(catalogId, categoryId));
        model.addAttribute("basicsHref", CatalogPaths.categoryBasics(catalogId, categoryId));
        model.addAttribute("reviewAction", CatalogPaths.productsAddReview(catalogId, categoryId));
        model.addAttribute("newProductHref", CatalogPaths.newProduct(catalogId, categoryId));
        model.addAttribute("backHref", CatalogPaths.category(catalogId, categoryId));
        return "catalog/products-add";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/add/review")
    public String reviewProducts(@PathVariable String catalogId, @PathVariable String categoryId,
                                 @RequestParam(required = false) List<String> eans, Model model, Locale locale,
                                 RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        if (eans == null || eans.isEmpty()) {
            redirectAttributes.addFlashAttribute(CatalogsController.ERROR_FLASH,
                    messageSource.getMessage("catalog.products.review.none", null, locale));
            return "redirect:" + CatalogPaths.productsAdd(catalogId, categoryId);
        }
        InventoryView enabled = inventory.withEnabledSuppliersOnly(storeId());
        List<Product> products = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String ean : eans) {
            MatchedInventory matched = enabled.findByEan(ean);
            // The proposals were read before the page was shown; a product can leave the inventory in the meantime.
            if (matched.isEmpty()) {
                skipped.add(ean);
                continue;
            }
            InventoryKey key = matched.getInventoryKey();
            Optional<PimEntry> entry = pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());
            products.add(new ProductRecommendation(category, matched, entry).toProduct());
        }
        return renderReview(catalog, category, new ProductsBulkAddForm(products), skipped, Map.of(), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/add/save")
    public String saveProducts(@PathVariable String catalogId, @PathVariable String categoryId,
                               @ModelAttribute ProductsBulkAddForm form, Model model, Locale locale,
                               RedirectAttributes redirectAttributes, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        Map<String, String> errors = form.validate(category.getGroupingOrder(), pricingGroups(category));
        if (!errors.isEmpty()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return renderReview(catalog, category, form, List.of(), errors, model, locale);
        }
        for (Product product : form.getProducts()) {
            // The category is the one in the address; what the form carried is never asked (rule of the settings pages).
            product.setCategoryId(category.getCategoryId());
            if (StringUtils.isBlank(product.getPimId())) {
                pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode()).ifPresent(entry -> {
                    product.setPimId(entry.pimId());
                    product.setBrand(brandMapper.unifyBrand(entry.brand()));
                });
            }
            productRepository.save(product);
        }
        SettingsFlash.onRedirect(redirectAttributes,
                messageSource.getMessage("catalog.products.added", new Object[]{form.getProducts().size()}, locale));
        return "redirect:" + CatalogPaths.category(catalogId, categoryId);
    }

    /** @param errors field id to message key; the page is given the texts, as the summary links to the fields. */
    private String renderReview(ProductCatalog catalog, CategoryDefinition category, ProductsBulkAddForm form,
                                List<String> skipped, Map<String, String> errors, Model model, Locale locale) {
        Map<String, String> texts = new LinkedHashMap<>();
        errors.forEach((field, key) -> texts.put(field, messageSource.getMessage(key, null, locale)));
        model.addAttribute("form", form);
        model.addAttribute("errors", texts);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("labels", category.getGroupingOrder());
        model.addAttribute("pricingGroups", pricingGroups(category));
        model.addAttribute("skipped", skipped);
        model.addAttribute("saveAction", CatalogPaths.productsAddSave(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backHref", CatalogPaths.productsAdd(catalog.getCatalogId(), category.getCategoryId()));
        return "catalog/products-add-review";
    }

    private static List<String> pricingGroups(CategoryDefinition category) {
        return category.getPriceDefinitions().stream().map(PriceDefinition::getPricingGroup).distinct().toList();
    }

    private List<ProductRow> rowsOf(ProductCatalog catalog, CategoryDefinition category) {
        if (category.hasType(CategoryDefinitionType.Dynamic)) {
            // Without PIM categories the engine has nothing to match, and reading the inventory would be wasted work.
            return category.hasCategoryMapping()
                    ? recommendationEngine.getRecommendations(category, inventory.withEnabledSuppliersOnly(storeId())).stream()
                            .map(recommendation -> ProductRow.ofRecommendation(recommendation, category))
                            .toList()
                    : List.of();
        }
        return productRepository.findAll(category.getCategoryId()).stream()
                .sorted(Comparator.comparing(Product::getLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Product::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(product -> ProductRow.of(product, category, catalog.getCatalogId(), marketplaces::displayName))
                .toList();
    }

    private String save(List<Product> products, boolean enabled) {
        products.forEach(product -> {
            product.setEnabled(enabled);
            productRepository.save(product);
        });
        return enabled ? "catalog.products.bulk.enabled" : "catalog.products.bulk.disabled";
    }

    private String delete(List<Product> products) {
        products.forEach(productRepository::delete);
        return "catalog.products.bulk.deleted";
    }

    /** Only a filter the page offers comes back in the address; anything else starts on the active products. */
    private static String statusFilter(String status) {
        return ProductStatus.fromFilter(status) != null || ALL.equals(status) ? status : "active";
    }

    private static String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
