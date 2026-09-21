package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.starter.util.PaginationUtil;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.*;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class ProductCatalogController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private Inventory inventory;

    @Autowired
    private PimCatalog pimCatalog;

    @Autowired
    private PimCategoryOptions pimCategoryOptions;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ProductCatalogDetailsService productCatalogDetailsService;

    @Autowired
    private BrandMapper brandMapper;

    @Value("${application.env}")
    private String env;

    @Autowired
    private MessageSource messageSource;

    private static final int CATALOGS_PAGE_SIZE = 25;
    private static final int PRODUCTS_PAGE_SIZE = 25;

    @GetMapping("/dashboard/catalogs")
    public String catalogs(Model model, @RequestParam(required = false, defaultValue = "1") int page) {
        List<ProductCatalog> productCatalogs = productCatalogRepository.findAll(getStoreId());
        List<ProductCatalog> paginatedProductCatalogs = PaginationUtil.paginate(productCatalogs, page, CATALOGS_PAGE_SIZE, model);
        model.addAttribute("productCatalogs", paginatedProductCatalogs);

        return "catalogs";
    }

    @GetMapping("/dashboard/catalogs/new")
    public String newCatalog(Model model) {
        return showEditProductCatalog(model, new ProductCatalog(getStoreId(), null));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}")
    public String getCatalogDetails(@PathVariable("catalogId") String catalogId, Model model) {
        return showEditProductCatalog(model, productCatalogRepository.findById(getStoreId(), catalogId));
    }

    private String showEditProductCatalog(Model model, ProductCatalog productCatalog) {
        model.addAttribute("productCatalog", productCatalog);
        model.addAttribute("scheduleMinIntervalMinutes", productCatalogDetailsService.minIntervalMinutes());
        return "catalogDetails";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/delete")
    public String deleteCatalog(@PathVariable String catalogId, RedirectAttributes redirectAttributes) {
        ProductCatalogDetailsService.UpdateResult result = productCatalogDetailsService.delete(getStoreId(), catalogId);
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", join(result));
            return "redirect:/dashboard/catalogs/" + catalogId;
        }
        return "redirect:/dashboard/catalogs";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}")
    public String saveCatalogDetails(@PathVariable String catalogId, @ModelAttribute ProductCatalog productCatalog,
                                     RedirectAttributes redirectAttributes) {
        ProductCatalogDetailsService.UpdateResult result = productCatalogDetailsService.save(getStoreId(), catalogId, productCatalog);
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", join(result));
        }
        return "redirect:/dashboard/catalogs/" + catalogId;
    }

    private String join(ProductCatalogDetailsService.UpdateResult result) {
        return result.errors().stream()
                .map(error -> messageSource.getMessage(error.code(), error.args(), LocaleContextHolder.getLocale()))
                .collect(Collectors.joining(" "));
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/new")
    public String newCategory(@PathVariable("catalogId") String catalogId, Model model) throws IllegalAccessException, InstantiationException {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);

        StockDefinition defaultStockDefinition = new StockDefinition(1, 10, 30);
        PriceDefinition defaultPriceDefinition = new PriceDefinition(1.00, 0, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP);
        MarketplaceDefinition defaultMarketplaceDefinition = new MarketplaceDefinition(null, 1.00, 30, 5, 3, 0, 0);
        AvailabilityDefinition defaultAvailabilityDefinition = new AvailabilityDefinition(3, 1);

        CategoryDefinition categoryDefinition = new CategoryDefinition()
                .withName(null)
                .withGeneratedId()
                .withSequenceNumber(productCatalog.getNextSequenceNumber())
                .withStockDefinition(defaultStockDefinition)
                .withPriceDefinition(defaultPriceDefinition)
                .withMarketplaceDefinition(defaultMarketplaceDefinition)
                .withAvailabilityDefinition(defaultAvailabilityDefinition);

        return showEditCategoryDefinitionForm(catalogId, model, categoryDefinition, false);
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}")
    public String editCategory(@PathVariable("catalogId") String catalogId, @PathVariable("categoryId") String categoryId, Model model) throws IllegalAccessException, InstantiationException {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition categoryDefinition = productCatalog.findCategoryDefinition(categoryId);
        return showEditCategoryDefinitionForm(catalogId, model, categoryDefinition, true);
    }

    private String showEditCategoryDefinitionForm(String catalogId, Model model, CategoryDefinition categoryDefinition, boolean isEdit) throws IllegalAccessException, InstantiationException {
        // this is a hack, we inject empty values to make fields editable and then filter out during saving
        categoryDefinition.getPriceDefinitions().add(new PriceDefinition());
        categoryDefinition.getMarketplaceDefinitions().add(new MarketplaceDefinition());
        // this is a hack, we inject empty values to make fields editable and then filter out during saving
        categoryDefinition.getGroupingOrder().add("");
        categoryDefinition.getGroupingOrder().add("");
        // this is a hack, we inject empty values to make fields editable and then filter out during saving
        List<Metadata> metadata = new LinkedList<>();
        metadata.add(new Metadata());
        metadata.add(new Metadata());
        metadata.add(new Metadata());

        InventoryDefinition defaultInventoryDefinition = new InventoryDefinition(InventoryFilterType.BRAND_NAME, metadata);
        categoryDefinition.getInventoryDefinitions().add(defaultInventoryDefinition);

        // this is a hack, we inject empty values to make fields editable and then filter out during saving
        categoryDefinition.getInventoryDefinitions().forEach(d -> d.getMetadata().add(new Metadata()));

        Store store = storesRepository.findById(getStoreId());

        model.addAttribute("inventoryFilterTypes", InventoryFilterType.values());
        model.addAttribute("inventoryDefinitionFilters", InventoryFilterType.getInstances());
        List<PimCategoryOptions.CategoryOption> categoryOptions = pimCategoryOptions.leafOptionsUnder(
                store.getEnabledCategories(), categoryDefinition.getPimCategoryIds());
        model.addAttribute("categoryOptions", categoryOptions);
        model.addAttribute("categoryAncestors", pimCategoryOptions.ancestorsOf(
                categoryOptions.stream().map(PimCategoryOptions.CategoryOption::id).toList()));
        model.addAttribute("selectedCategoryOptions", selectedOptions(categoryDefinition));
        model.addAttribute("categoryDefinitionTypes", CategoryDefinitionType.values());
        model.addAttribute("categoryDefinition", categoryDefinition);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("marketplaceTypes", store.getMarketplaces().stream().map(MarketplaceIntegration::getName).toList());
        model.addAttribute("edit", isEdit);

        return "catalogDetails_categoryDefinition";
    }

    private List<PimCategoryOptions.CategoryOption> selectedOptions(CategoryDefinition categoryDefinition) {
        return pimCategoryOptions.optionsOf(categoryDefinition.getPimCategoryIds());
    }

    private String displayCategories(CategoryDefinition definition) {
        if (!definition.hasCategoryMapping()) {
            return StringUtils.defaultString(definition.getCategory());
        }
        return String.join(", ", pimCategoryOptions.namesOf(definition.getPimCategoryIds()));
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category")
    public String saveCategoryDefinition(@PathVariable String catalogId, @ModelAttribute CategoryDefinition categoryDefinition, Model model, RedirectAttributes redirectAttributes) {
        if (StringUtils.isBlank(categoryDefinition.getCategory())) {
            categoryDefinition.setCategory(null);
        }
        if (categoryDefinition.isComplete()) {
            // Save the category definition
            ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
            productCatalog.addOrUpdateCategoryDefinition(categoryDefinition);
            productCatalogRepository.save(productCatalog);
            warnWhenCategoryHasNoInventory(categoryDefinition, redirectAttributes);
        } else {
            throw new RuntimeException("Category definition is not complete");
        }

        return "redirect:/dashboard/catalogs/" + catalogId;
    }

    private void warnWhenCategoryHasNoInventory(CategoryDefinition categoryDefinition, RedirectAttributes redirectAttributes) {
        if (!categoryDefinition.hasType(CategoryDefinitionType.Dynamic)) {
            return;
        }
        if (!categoryDefinition.hasCategoryMapping()) {
            redirectAttributes.addFlashAttribute("warningMessage", messageSource.getMessage(
                    "catalog.category.noMapping", null, LocaleContextHolder.getLocale()));
            return;
        }
        Map<String, Collection<MatchedInventory>> matchesByCategoryId = inventory.withEnabledSuppliersOnly(getStoreId())
                .findAllByProductCategoryIds(categoryDefinition.getPimCategoryIds());
        List<String> emptyCategoryIds = categoryDefinition.getPimCategoryIds().stream()
                .filter(id -> matchesByCategoryId.getOrDefault(id, List.of()).stream().noneMatch(MatchedInventory::hasAnyOffers))
                .toList();
        if (!emptyCategoryIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("warningMessage", messageSource.getMessage(
                    "catalog.category.emptyInventory",
                    new Object[]{String.join(", ", pimCategoryOptions.namesOf(emptyCategoryIds))},
                    LocaleContextHolder.getLocale()));
        }
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/delete")
    public String deleteCategoryDefinition(@PathVariable String catalogId, @PathVariable String categoryId) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition removedCategoryDefinition = productCatalog.removeCategoryDefinition(categoryId);

        boolean keepProducts = removedCategoryDefinition.hasCategoryMapping()
                && productCatalog.getCategories().stream()
                .anyMatch(c -> c.getPimCategoryIds().stream()
                        .anyMatch(removedCategoryDefinition.getPimCategoryIds()::contains));
        if (!keepProducts) {
            List<Product> products = productRepository.findAll(removedCategoryDefinition.getCategoryId());
            productRepository.delete(products);
        }

        productCatalogRepository.save(productCatalog);

        return "redirect:/dashboard/catalogs/" + catalogId;
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/new")
    public String newProduct(@PathVariable String catalogId, @PathVariable String categoryId, Model model) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition categoryDefinition = productCatalog.findCategoryDefinition(categoryId);

        Product product = new Product(categoryDefinition.getCategoryId());
        return showEditProductForm( model,catalogId, product, categoryDefinition);
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/bulk-create")
    public String createProducts(@PathVariable String catalogId, @PathVariable String categoryId,
                                 @ModelAttribute ProductsBulkAddForm form,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        form.getProducts().forEach(product -> {
            if (StringUtils.isBlank(product.getPimId())) {
                pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode())
                        .ifPresent(entry -> {
                            product.setPimId(entry.pimId());
                            product.setBrand(brandMapper.unifyBrand(entry.brand()));
                        });
            }
            productRepository.save(product);
        });

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage(
                "catalog.category.product.bulk.add.success", new Object[]{form.getProducts().size()}, locale));
        return "redirect:/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/recommendations";
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}")
    public String editProduct(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String productId, Model model) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition categoryDefinition = productCatalog.findCategoryDefinition(categoryId);
        Product product = productRepository.findByProductId(categoryDefinition.getCategoryId(), productId);

        return showEditProductForm(model,catalogId, product, categoryDefinition);
    }

    private String showEditProductForm(Model model, String catalogId,
                                       Product product, CategoryDefinition categoryDefinition) {
        // this is a hack, we inject empty values to make fields editable and then filter out during saving
        product.getCustomAttributesFilters().add(new ProductCustomAttributeFilter());
        product.getCustomAttributesFilters().add(new ProductCustomAttributeFilter());
        product.getCustomAttributes().add(new ProductCustomAttribute());
        product.getCustomAttributes().add(new ProductCustomAttribute());
        product.getMetadata().add(new Metadata());
        product.getMetadata().add(new Metadata());
        product.getQuickFilters().add("");
        product.getQuickFilters().add("");
        product.getQuickFilters().add("");
        product.getQuickFilters().add("");

        Store store = storesRepository.findById(getStoreId());

        List<PimCategoryOptions.CategoryOption> productCategories = pimCategoryOptions.namedOptions(
                store.getEnabledCategories(),
                product.getCustomAttributesFilters().stream().map(ProductCustomAttributeFilter::getCategory).toList());
        model.addAttribute("productCategories", productCategories);
        model.addAttribute("categoryAncestors", pimCategoryOptions.ancestorsOfNames(
                productCategories.stream().map(PimCategoryOptions.CategoryOption::name).toList()));
        model.addAttribute("pricingGroups", categoryDefinition.getPriceDefinitions().stream().map(PriceDefinition::getPricingGroup).distinct().collect(Collectors.toList()));
        model.addAttribute("labels", categoryDefinition.getGroupingOrder());
        model.addAttribute("availabilityTypes", ProductAvailabilityType.values());
        model.addAttribute("marketplaceTypes", store.getMarketplaces().stream().map(MarketplaceIntegration::getName).toList());
        model.addAttribute("product", product);
        model.addAttribute("catalogId", catalogId);

        return "catalogDetails_categoryDefinition_productDetails";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}")
    public String saveProduct(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String productId, @ModelAttribute Product product, Model model) {
        Product existingProduct = productRepository.findByProductId(categoryId, productId);

        if (existingProduct == null) {
            existingProduct = product;

            if (StringUtils.isBlank(product.getPimId())) {
                pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode())
                        .ifPresent(entry -> {
                            product.setPimId(entry.pimId());
                            product.setBrand(brandMapper.unifyBrand(entry.brand()));
                        });
            }
        }

        // Check if EAN requires PIM ID change
        validateIfPimChangeIsRequired(product, existingProduct);

        existingProduct.setLabel(product.getLabel());
        existingProduct.setName(product.getName());
        existingProduct.setRecommendation(product.getRecommendation());
        existingProduct.setEnabled(product.isEnabled());
        existingProduct.setService(product.isService());

        existingProduct.setCustomAttributesFilters(product.getCustomAttributesFilters()
                .stream()
                .filter(ProductCustomAttributeFilter::isComplete)
                .collect(Collectors.toList())
        );
        existingProduct.setCustomAttributes(product.getCustomAttributes()
                .stream()
                .filter(ProductCustomAttribute::isComplete)
                .collect(Collectors.toList())
        );
        existingProduct.setQuickFilters(product.getQuickFilters()
                .stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList())
        );
        existingProduct.setMetadata(product.getMetadata()
                .stream()
                .filter(Metadata::isComplete)
                .collect(Collectors.toList())
        );

        existingProduct.setAvailabilityType(product.getAvailabilityType());
        existingProduct.setSuggestedRetailPrice(product.getSuggestedRetailPrice());
        existingProduct.setMaxRetailPrice(product.getMaxRetailPrice());
        existingProduct.setMaxRetailPriceSuppliers(product.getMaxRetailPriceSuppliers());
        existingProduct.setStockExpectedQty(product.getStockExpectedQty());
        existingProduct.setRestockPricePromo(product.getRestockPricePromo());
        existingProduct.setRestockPriceStandard(product.getRestockPriceStandard());
        existingProduct.setEstimatedDeliveryDays(product.getEstimatedDeliveryDays());
        existingProduct.setPricingGroup(product.getPricingGroup());
        existingProduct.setMarketplaces(product.getMarketplaces());

        productRepository.save(existingProduct);

        return "redirect:/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/products/" + productId;
    }

    private void validateIfPimChangeIsRequired(Product product, Product existingProduct) {
        if (isNotBlank(existingProduct.getPimId())) {
            validatePimIdChange(existingProduct.getPimId(), product.getEan(), product.getManufacturerCode());
        }

        existingProduct.setEan(product.getEan());
        existingProduct.setManufacturerCode(product.getManufacturerCode());
    }

    private void validatePimIdChange(String existingPimId, String ean, String manufacturerCode) {
        String newPimId = pimCatalog.findByGtinOrMpn(ean, manufacturerCode)
                .map(PimEntry::pimId)
                .orElse(null);

        if (!StringUtils.equals(existingPimId, newPimId)) {
            throw new RuntimeException("Product identifier cannot be changed, as it would require changing PIM ID");
        }
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{productId}/delete")
    public String deleteProduct(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String productId) {
        Product product = productRepository.findByProductId(categoryId, productId);
        productRepository.delete(product);
        return "redirect:/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/products";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/bulk-execute")
    public String bulkExecuteProducts(@PathVariable String catalogId, @PathVariable String categoryId,
                                     @RequestParam(required = false) List<String> productIds, @RequestParam String action,
                                     RedirectAttributes redirectAttributes, Locale locale,
                                     @RequestParam(value = "status", defaultValue = "Enabled") String status) {

        if (productIds == null || productIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("bulk.execute.no.items.selected", null, locale));
            return "redirect:/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/products?status=" + status;
        }

        List<Product> products = productIds.stream()
                .map(productId -> productRepository.findByProductId(categoryId, productId))
                .collect(Collectors.toList());

        switch (action.toLowerCase()) {
            case "enable":
                update(products, true);
                break;
            case "disable":
                update(products, false);
                break;
            case "delete":
                products.forEach(productRepository::delete);
                break;
            default:
                throw new IllegalArgumentException("Invalid action: " + action);
        }

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("general.success", null, locale));
        return "redirect:/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/products?status=" + status;
    }

    private void update(List<Product> products, boolean enabled) {
        products.forEach(product -> {
            product.setEnabled(enabled);
            productRepository.save(product);
        });
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
