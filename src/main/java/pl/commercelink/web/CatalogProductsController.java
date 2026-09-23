package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
import pl.commercelink.products.ProductAvailabilityType;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCustomAttributeFilter;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.catalog.CategoryPageModel;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.catalog.ProductRow;
import pl.commercelink.web.catalog.ProductStatus;
import pl.commercelink.web.catalog.RecommendationRow;
import pl.commercelink.web.dtos.ProductForm;
import pl.commercelink.web.dtos.ProductsBulkAddForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Products of a catalog category: the category page (the products table with its filters), the bulk actions on it,
 * adding products from the inventory (proposals, the review of their data, the save) and the page of one product.
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

    /** A category proposes hundreds of products and every one of them can be selected, well past Spring's default 256. */
    private static final int MAX_ADDED_PRODUCTS = 5000;

    private static final String PRODUCT_VIEW = "catalog/product";
    private static final String PRODUCT_FRAGMENT = PRODUCT_VIEW + " :: productForm";

    private final CatalogAccess access;
    private final ProductRepository productRepository;
    private final StoresRepository storesRepository;
    private final ProductRecommendationEngine recommendationEngine;
    private final Inventory inventory;
    private final MarketplaceConnections marketplaces;
    private final PimCategoryOptions pimCategoryOptions;
    private final SupplierLabels supplierLabels;
    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;
    private final MessageSource messageSource;

    /**
     * Raised for the review form, whose list grows to one entry per selected proposal; Spring stops at 256 by default
     * and a category proposing more than that would answer a full selection with an error. Set for every binder of
     * this controller: the target is not known yet when the binder is initialised, and nothing else here binds a list.
     */
    @InitBinder
    void allowLargeSelections(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(MAX_ADDED_PRODUCTS);
    }

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
    public String addProducts(@PathVariable String catalogId, @PathVariable String categoryId, Model model,
                              Locale locale, RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
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
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        if (eans == null || eans.isEmpty()) {
            redirectAttributes.addFlashAttribute(CatalogsController.ERROR_FLASH,
                    messageSource.getMessage("catalog.products.review.none", null, locale));
            return "redirect:" + CatalogPaths.productsAdd(catalogId, categoryId);
        }
        InventoryView enabled = inventory.withEnabledSuppliersOnly(storeId());
        // Read once: the same selection sent twice (Back, a double click) must not add the product a second time.
        List<InventoryKey> alreadyInCategory = productRepository.findAll(category.getCategoryId()).stream()
                .map(InventoryKey::fromProduct)
                .toList();
        List<Product> products = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> skippedExisting = new ArrayList<>();
        for (String ean : eans) {
            MatchedInventory matched = enabled.findByEan(ean);
            // The proposals were read before the page was shown; a product can leave the inventory in the meantime.
            if (matched.isEmpty()) {
                skipped.add(ean);
                continue;
            }
            InventoryKey key = matched.getInventoryKey();
            if (alreadyInCategory.stream().anyMatch(key::matches)) {
                skippedExisting.add(ean);
                continue;
            }
            Optional<PimEntry> entry = pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());
            products.add(new ProductRecommendation(category, matched, entry).toProduct());
        }
        return renderReview(catalog, category, ProductsBulkAddForm.of(products), skipped, skippedExisting, Map.of(),
                model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/add/save")
    public String saveProducts(@PathVariable String catalogId, @PathVariable String categoryId,
                               @ModelAttribute ProductsBulkAddForm form, Model model, Locale locale,
                               RedirectAttributes redirectAttributes, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        Map<String, String> errors = form.validate(category.getGroupingOrder(), pricingGroups(category));
        if (!errors.isEmpty()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return renderReview(catalog, category, form, List.of(), List.of(), errors, model, locale);
        }
        // The review skipped what the category had when it was rendered; the same review sent again (Back, a double
        // click) is decided here once more, against the category as it is now.
        List<InventoryKey> alreadyInCategory = new ArrayList<>(productRepository.findAll(category.getCategoryId()).stream()
                .map(InventoryKey::fromProduct)
                .toList());
        InventoryView enabled = inventory.withEnabledSuppliersOnly(storeId());
        int added = 0;
        for (ProductsBulkAddForm.Row row : form.getProducts()) {
            // The category and the id are the application's to give, and so is the PIM entry: a pim id taken from the
            // form would bind the product to an arbitrary entry of the catalog.
            Product product = row.toProduct(category.getCategoryId());
            InventoryKey key = InventoryKey.fromProduct(product);
            if (alreadyInCategory.stream().anyMatch(key::matches)) {
                continue;
            }
            pimEntryOf(enabled, key, product).ifPresent(entry -> {
                product.setPimId(entry.pimId());
                product.setBrand(brandMapper.unifyBrand(entry.brand()));
            });
            productRepository.save(product);
            alreadyInCategory.add(key);
            added++;
        }
        SettingsFlash.onRedirect(redirectAttributes, added == 0
                ? messageSource.getMessage("catalog.products.added.none", null, locale)
                : messageSource.getMessage("catalog.products.added", new Object[]{added}, locale));
        return "redirect:" + CatalogPaths.category(catalogId, categoryId);
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/new")
    public String newProduct(@PathVariable String catalogId, @PathVariable String categoryId,
                             @RequestParam(required = false) String ean,
                             @RequestParam(required = false, defaultValue = "active") String status, Model model,
                             Locale locale, RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        Store store = storesRepository.findById(storeId());
        String currentStatus = statusFilter(status);
        if (StringUtils.isBlank(ean)) {
            return renderProduct(catalog, category, store, null, ProductForm.forNewProduct(), null, Map.of(), null,
                    currentStatus, model, locale);
        }
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(storeId()).findByEan(ean);
        // The link was followed from a list read earlier; a product can leave the inventory in the meantime, and that
        // is no reason to refuse the page — the form simply opens empty and says why.
        if (matched.isEmpty()) {
            return renderProduct(catalog, category, store, null, ProductForm.forNewProduct(), null, Map.of(),
                    messageSource.getMessage("catalog.products.new.eanNotInInventory", new Object[]{ean}, locale),
                    currentStatus, model, locale);
        }
        InventoryKey key = matched.getInventoryKey();
        Optional<PimEntry> entry = pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());
        Product prefilled = new ProductRecommendation(category, matched, entry).toProduct();
        ProductForm form = ProductForm.from(prefilled);
        // Nothing is saved yet, so there is no identity to protect: the PIM entry is resolved again when it is.
        form.setExistingPimId(null);
        form.setExistingLabel(null);
        return renderProduct(catalog, category, store, null, form, prefilled.getPimId(), Map.of(), null,
                currentStatus, model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/new")
    public String createProduct(@PathVariable String catalogId, @PathVariable String categoryId,
                                @ModelAttribute ProductForm form,
                                @RequestParam(required = false, defaultValue = "active") String status,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        Store store = storesRepository.findById(storeId());
        boolean async = SettingsPaths.isAsync(requestedWith);
        String currentStatus = statusFilter(status);
        form.setExistingPimId(null);
        form.setExistingLabel(null);
        Map<String, String> errors = form.validate(category.getGroupingOrder(), pricingGroups(category),
                marketplaceNames(store), this::pimIdFor);
        if (!errors.isEmpty()) {
            return rejected(renderProduct(catalog, category, store, null, form, null, errors, null, currentStatus,
                    model, locale), PRODUCT_FRAGMENT, async, response);
        }
        Product product = form.toNewProduct(category.getCategoryId());
        pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode()).ifPresent(entry -> {
            product.setPimId(entry.pimId());
            product.setBrand(brandMapper.unifyBrand(entry.brand()));
        });
        productRepository.save(product);
        return saved(CatalogPaths.category(catalogId, categoryId) + "?status=" + currentStatus,
                messageSource.getMessage("product.added", new Object[]{product.getName()}, locale), async, model,
                redirectAttributes, request, response, PRODUCT_FRAGMENT,
                () -> renderProduct(catalog, category, store, null, form, product.getPimId(), Map.of(), null,
                        currentStatus, model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}")
    public String product(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String productId,
                          @RequestParam(required = false, defaultValue = "active") String status, Model model,
                          Locale locale, RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        // A missing product is a 404 regardless of the category's type; only once it is known to exist can the
        // category being automatic (nothing to edit there) redirect instead.
        Product product = access.requireProduct(category, productId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        return renderProduct(catalog, category, storesRepository.findById(storeId()), product, ProductForm.from(product),
                product.getPimId(), Map.of(), null, statusFilter(status), model, locale);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}")
    public String saveProduct(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String productId,
                              @ModelAttribute ProductForm form,
                              @RequestParam(required = false, defaultValue = "active") String status,
                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                              Model model, Locale locale, RedirectAttributes redirectAttributes,
                              HttpServletRequest request, HttpServletResponse response) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        Product product = access.requireProduct(category, productId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        Store store = storesRepository.findById(storeId());
        boolean async = SettingsPaths.isAsync(requestedWith);
        String currentStatus = statusFilter(status);
        // Identity and brand belong to the saved product, not to the request: a forged PIM id would let the offer of
        // this product claim another entry.
        form.setExistingPimId(product.getPimId());
        form.setExistingLabel(product.getLabel());
        Map<String, String> errors = form.validate(category.getGroupingOrder(), pricingGroups(category),
                marketplaceNames(store), this::pimIdFor);
        if (!errors.isEmpty()) {
            return rejected(renderProduct(catalog, category, store, product, form, product.getPimId(), errors, null,
                    currentStatus, model, locale), PRODUCT_FRAGMENT, async, response);
        }
        form.applyTo(product);
        productRepository.save(product);
        return saved(CatalogPaths.category(catalogId, categoryId) + "?status=" + currentStatus,
                messageSource.getMessage("product.saved", new Object[]{product.getName()}, locale), async, model,
                redirectAttributes, request, response, PRODUCT_FRAGMENT,
                () -> renderProduct(catalog, category, store, product, form, product.getPimId(), Map.of(), null,
                        currentStatus, model, locale));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}/delete")
    public String confirmDeleteProduct(@PathVariable String catalogId, @PathVariable String categoryId,
                                       @PathVariable String productId, Model model, Locale locale,
                                       RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        Product product = access.requireProduct(category, productId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("product.delete.title", new Object[]{product.getName()}, locale),
                messageSource.getMessage("product.delete.message", null, locale),
                messageSource.getMessage("product.delete", null, locale),
                CatalogPaths.productDelete(catalogId, categoryId, productId),
                CatalogPaths.product(catalogId, categoryId, productId)));
        model.addAttribute("backLabel", product.getName());
        return "settings-confirm";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}/delete")
    public String deleteProduct(@PathVariable String catalogId, @PathVariable String categoryId,
                                @PathVariable String productId, Locale locale, RedirectAttributes redirectAttributes) {
        ProductCatalog catalog = access.requireCatalog(storeId(), catalogId);
        CategoryDefinition category = access.requireCategory(catalog, categoryId);
        Product product = access.requireProduct(category, productId);
        String refused = refuseAutomatic(category, catalogId, categoryId, locale, redirectAttributes);
        if (refused != null) {
            return refused;
        }
        productRepository.delete(product);
        SettingsFlash.onRedirect(redirectAttributes,
                messageSource.getMessage("product.deleted", new Object[]{product.getName()}, locale));
        return "redirect:" + CatalogPaths.category(catalogId, categoryId);
    }

    /**
     * The PIM entry of a product being added, resolved the way the review resolved it: through the inventory key the
     * suppliers know the item by, which carries every EAN and product code listed under it. Asking with the two
     * identifiers of the row alone would miss an entry the catalogue holds under a sibling code, and the product would
     * be saved without a pim id -- out of the price list and out of every marketplace offer.
     *
     * <p>The row's own two codes are asked about whenever the key answers nothing: the product may have left the
     * inventory between the review and the save, and the key the inventory does know it by may not carry the
     * identifier the operator has just corrected in the review.
     */
    private Optional<PimEntry> pimEntryOf(InventoryView inventory, InventoryKey key, Product product) {
        MatchedInventory matched = inventory.findByInventoryKey(key);
        if (!matched.isEmpty()) {
            InventoryKey known = matched.getInventoryKey();
            Optional<PimEntry> byInventoryKey = pimCatalog.findByPimIdOrGtinsOrMpns(
                    known.getId(), known.getProductEans(), known.getProductCodes());
            if (byInventoryKey.isPresent()) {
                return byInventoryKey;
            }
        }
        return pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode());
    }

    /** The PIM entry the submitted identifiers point at, which the saved product's own entry is compared with. */
    private Optional<String> pimIdFor(ProductForm.PimCheck check) {
        return pimCatalog.findByGtinOrMpn(check.ean(), check.mfn()).map(PimEntry::pimId);
    }

    private static List<String> marketplaceNames(Store store) {
        return store.getMarketplaces().stream().map(MarketplaceIntegration::getName).toList();
    }

    /**
     * @param existing     the saved product, or null for one being created
     * @param pimId        the entry the product resolves to, or null when it has none; never taken from the form
     * @param notice       a sentence about how the form was filled (an EAN the inventory no longer has), or null
     * @param statusFilter the status the category page was showing when this page was reached, carried through the
     *                     form so the redirect after a save returns to the same filter
     */
    private String renderProduct(ProductCatalog catalog, CategoryDefinition category, Store store, Product existing,
                                 ProductForm form, String pimId, Map<String, String> errors, String notice,
                                 String statusFilter, Model model, Locale locale) {
        boolean edit = existing != null;
        String catalogId = catalog.getCatalogId();
        String categoryId = category.getCategoryId();
        List<PimCategoryOptions.CategoryOption> productCategories = pimCategoryOptions.namedOptions(
                store.getEnabledCategories(),
                form.getCustomAttributesFilters().stream().map(ProductCustomAttributeFilter::getCategory).toList());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("existing", edit);
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("prefillNotice", notice);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("labels", category.getGroupingOrder());
        model.addAttribute("pricingGroups", pricingGroups(category));
        model.addAttribute("availabilityTypes", Arrays.stream(ProductAvailabilityType.values()).map(Enum::name).toList());
        model.addAttribute("storeMarketplaces", store.getMarketplaces().stream()
                .map(integration -> Map.of("name", integration.getName(),
                        "displayName", marketplaces.displayName(integration.getName())))
                .toList());
        model.addAttribute("productCategories", productCategories);
        model.addAttribute("categoryAncestors", pimCategoryOptions.ancestorsOfNames(
                productCategories.stream().map(PimCategoryOptions.CategoryOption::name).toList()));
        model.addAttribute("formAction", edit
                ? CatalogPaths.product(catalogId, categoryId, existing.getProductId())
                : CatalogPaths.newProduct(catalogId, categoryId));
        model.addAttribute("backHref", CatalogPaths.category(catalogId, categoryId));
        model.addAttribute("pageTitle", edit
                ? existing.getName() : messageSource.getMessage("product.page.new", null, locale));
        model.addAttribute("deleteHref", edit
                ? CatalogPaths.productDelete(catalogId, categoryId, existing.getProductId()) : null);
        model.addAttribute("productId", edit ? existing.getProductId() : null);
        model.addAttribute("leadParts", lead(form, pimId, locale));
        // Rarely used sections open by themselves when they hold something, and whenever they hold a mistake to fix.
        model.addAttribute("openStock", form.hasStockOrMarketplaceValues()
                || errors.containsKey("stockExpectedQty") || errors.containsKey("restockPricePromo")
                || errors.containsKey("restockPriceStandard") || errors.containsKey("marketplaces"));
        model.addAttribute("openClient", form.hasClientData() || errors.keySet().stream()
                .anyMatch(field -> field.startsWith(ProductForm.ATTRIBUTE) || field.startsWith(ProductForm.FILTER)
                        || field.startsWith(ProductForm.METADATA)));
        return PRODUCT_VIEW;
    }

    /** One part of the line under the product title; {@code warn} shows it as a warning pill. */
    public record LeadPart(String text, boolean warn) {
    }

    /**
     * What the product is, in one line: its identifiers, its PIM entry (or that it has none) and its brand. Plain text
     * parts; the template escapes them and draws the missing entry as a pill.
     */
    private List<LeadPart> lead(ProductForm form, String pimId, Locale locale) {
        List<LeadPart> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(form.getEan())) {
            parts.add(new LeadPart("EAN " + form.getEan(), false));
        }
        if (StringUtils.isNotBlank(form.getManufacturerCode())) {
            parts.add(new LeadPart(form.getManufacturerCode(), false));
        }
        parts.add(StringUtils.isNotBlank(pimId)
                ? new LeadPart("PIM " + pimId, false)
                : new LeadPart(messageSource.getMessage("catalog.products.add.noPimEntry", null, locale), true));
        if (StringUtils.isNotBlank(form.getBrand())) {
            parts.add(new LeadPart(form.getBrand(), false));
        }
        return parts;
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

    /** @param errors field id to message key; the page is given the texts, as the summary links to the fields. */
    private String renderReview(ProductCatalog catalog, CategoryDefinition category, ProductsBulkAddForm form,
                                List<String> skipped, List<String> skippedExisting, Map<String, String> errors,
                                Model model, Locale locale) {
        Map<String, String> texts = new LinkedHashMap<>();
        errors.forEach((field, key) -> texts.put(field, messageSource.getMessage(key, null, locale)));
        model.addAttribute("form", form);
        model.addAttribute("errors", texts);
        model.addAttribute("errorSummary", ProductsBulkAddForm.summary(texts, (number, text) ->
                messageSource.getMessage(ProductsBulkAddForm.SUMMARY_LINE, new Object[]{number, text}, locale)));
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("labels", category.getGroupingOrder());
        model.addAttribute("pricingGroups", pricingGroups(category));
        model.addAttribute("skipped", skipped);
        model.addAttribute("skippedExisting", skippedExisting);
        model.addAttribute("saveAction", CatalogPaths.productsAddSave(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backHref", CatalogPaths.productsAdd(catalog.getCatalogId(), category.getCategoryId()));
        return "catalog/products-add-review";
    }

    /**
     * Products are kept by hand in a manual category only: an automatic one computes its list from the inventory, so a
     * saved product would show up neither there nor among the proposals, and there is nothing to edit or delete.
     */
    private String refuseAutomatic(CategoryDefinition category, String catalogId, String categoryId, Locale locale,
                                   RedirectAttributes redirectAttributes) {
        if (!category.hasType(CategoryDefinitionType.Dynamic)) {
            return null;
        }
        redirectAttributes.addFlashAttribute(CatalogsController.ERROR_FLASH,
                messageSource.getMessage("catalog.products.add.dynamic", null, locale));
        return "redirect:" + CatalogPaths.category(catalogId, categoryId);
    }

    private static List<String> pricingGroups(CategoryDefinition category) {
        return category.getPriceDefinitions().stream().map(PriceDefinition::getPricingGroup).distinct().toList();
    }

    private List<ProductRow> rowsOf(ProductCatalog catalog, CategoryDefinition category) {
        if (category.hasType(CategoryDefinitionType.Dynamic)) {
            // Without PIM categories the engine has nothing to match, and reading the inventory would be wasted work.
            // The engine answers in its own order (brand, then price); both kinds of category read the same way.
            return category.hasCategoryMapping()
                    ? recommendationEngine.getRecommendations(category, inventory.withEnabledSuppliersOnly(storeId())).stream()
                            .sorted(byLabelThenName(ProductRecommendation::getLabel, ProductRecommendation::getName))
                            .map(recommendation -> ProductRow.ofRecommendation(recommendation, category))
                            .toList()
                    : List.of();
        }
        Set<String> connected = marketplaceNames(storesRepository.findById(storeId())).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return productRepository.findAll(category.getCategoryId()).stream()
                .sorted(byLabelThenName(Product::getLabel, Product::getName))
                .map(product -> ProductRow.of(product, category, catalog.getCatalogId(), marketplaces::displayName, connected))
                .toList();
    }

    /** The order of every product list: by label, then by name, regardless of case; a missing value goes last. */
    private static <T> Comparator<T> byLabelThenName(Function<T, String> label, Function<T, String> name) {
        return Comparator.comparing(label, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
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
