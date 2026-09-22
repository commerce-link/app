package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.catalog.CategoryPageModel;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.catalog.ProductRow;
import pl.commercelink.web.catalog.ProductStatus;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Products of a catalog category: the category page (the products table with its filters) and the bulk actions on it. */
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
