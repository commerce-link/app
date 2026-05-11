package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
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
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.PricelistEventScheduler;
import pl.commercelink.products.*;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static pl.commercelink.starter.util.ConversionUtil.asDistinctCollectionFromStream;
import static pl.commercelink.starter.util.ConversionUtil.guessValueType;

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
    private ProductRecommendationEngine recommendationEngine;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private PricelistEventScheduler pricelistEventScheduler;

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
        return "catalogDetails";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/delete")
    public String deleteCatalog(@PathVariable String catalogId) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        pricelistEventScheduler.deleteSchedule(productCatalog.getStoreId(), catalogId);

        List<Product> products = productRepository.findAll(productCatalog);
        productRepository.delete(products);
        productCatalogRepository.delete(productCatalog);
        return "redirect:/dashboard/catalogs";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}")
    public String saveCatalogDetails(@PathVariable String catalogId, @ModelAttribute ProductCatalog productCatalog, Model model) {
        ProductCatalog catalog = productCatalogRepository.findById(getStoreId(), catalogId);
        if (catalog == null) {
            pricelistEventScheduler.createRecurringSchedule(getStoreId(), catalogId);

            catalog = productCatalog;
        }

        catalog.setName(productCatalog.getName());
        catalog.setDeletionProtection(productCatalog.isDeletionProtection());

        productCatalogRepository.save(catalog);

        return "redirect:/dashboard/catalogs/" + catalogId;
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/new")
    public String newCategory(@PathVariable("catalogId") String catalogId, Model model) throws IllegalAccessException, InstantiationException {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);

        StockDefinition defaultStockDefinition = new StockDefinition(1, 10, 30);
        PriceDefinition defaultPriceDefinition = new PriceDefinition(1.00, 0, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP);
        MarketplaceDefinition defaultMarketplaceDefinition = new MarketplaceDefinition(null, 1.00, 30, 5, 3, 0);
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
        model.addAttribute("productCategories", store.getEnabledProductCategories());
        model.addAttribute("categoryDefinitionTypes", CategoryDefinitionType.values());
        model.addAttribute("categoryDefinition", categoryDefinition);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("marketplaceTypes", store.getMarketplaces().stream().map(MarketplaceIntegration::getName).toList());
        model.addAttribute("edit", isEdit);

        return "catalogDetails_categoryDefinition";
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category")
    public String saveCategoryDefinition(@PathVariable String catalogId, @ModelAttribute CategoryDefinition categoryDefinition, Model model) {
        if (categoryDefinition.isComplete()) {
            // Save the category definition
            ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
            productCatalog.addOrUpdateCategoryDefinition(categoryDefinition);
            productCatalogRepository.save(productCatalog);
        } else {
            throw new RuntimeException("Category definition is not complete");
        }

        return "redirect:/dashboard/catalogs/" + catalogId + "/category/" + categoryDefinition.getCategoryId();
    }

    @PostMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/delete")
    public String deleteCategoryDefinition(@PathVariable String catalogId, @PathVariable String categoryId) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition removedCategoryDefinition = productCatalog.removeCategoryDefinition(categoryId);

        // if no other CategoryDefinition is associated with same ProductCategory, then delete all products associated with this category
        if (productCatalog.getCategories().stream().noneMatch(c -> c.getCategory() == removedCategoryDefinition.getCategory())) {
            List<Product> products = productRepository.findAll(removedCategoryDefinition.getCategoryId());
            productRepository.delete(products);
        }

        productCatalogRepository.save(productCatalog);

        return "redirect:/dashboard/catalogs/" + catalogId;
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/recommendations")
    public String showRecommendations(@PathVariable String catalogId, @PathVariable String categoryId,
                                      @RequestParam(required = false, defaultValue = "1") int page,
                                      @RequestParam(required = false) String brand,
                                      @RequestParam(required = false) String label,
                                      @RequestParam(required = false) String pimId,
                                      @RequestParam(required = false) String ean,
                                      @RequestParam(required = false) String mfn,
                                      Model model) {
        ProductCatalog catalog = productCatalogRepository.findById(getStoreId(),catalogId);
        CategoryDefinition categoryDefinition = catalog.findCategoryDefinition(categoryId);

        List<ProductRecommendation> recommendations = recommendationEngine.getRecommendations(categoryDefinition, inventory.withEnabledSuppliersOnly(getStoreId()));

        // Apply filtering similar to showProducts
        List<ProductRecommendation> filteredRecommendations = recommendations.stream()
                .filter(r -> !isNotBlank(brand) || StringUtils.equalsIgnoreCase(r.getBrand(), brand))
                .filter(r -> !isNotBlank(label) || StringUtils.equalsIgnoreCase(r.getLabel(), label))
                .filter(r -> !isNotBlank(pimId) || StringUtils.equalsIgnoreCase(r.getPimId(), pimId))
                .filter(r -> !isNotBlank(ean) || StringUtils.equalsIgnoreCase(r.getEan(), ean))
                .filter(r -> !isNotBlank(mfn) || StringUtils.equalsIgnoreCase(r.getManufacturerCode(), mfn))
                .collect(Collectors.toList());

        Collection<String> brands = asDistinctCollectionFromStream(filteredRecommendations.stream()
                .map(ProductRecommendation::getBrand));

        List<ProductRecommendation> paginatedRecommendations = PaginationUtil.paginate(
                filteredRecommendations, page, PRODUCTS_PAGE_SIZE, model
        );

        // Add pagination parameters for filtering
        Map<String, Object> paginationParams = new HashMap<>();
        if (StringUtils.isNotBlank(brand)) paginationParams.put("brand", brand);
        if (StringUtils.isNotBlank(pimId)) paginationParams.put("pimId", pimId);
        if (StringUtils.isNotBlank(ean)) paginationParams.put("ean", ean);
        if (StringUtils.isNotBlank(mfn)) paginationParams.put("mfn", mfn);

        model.addAttribute("brands", brands);
        model.addAttribute("productRecommendations", paginatedRecommendations);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("paginationParams", paginationParams);

        return "catalogDetails_categoryDefinition_productRecommendations";
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/new")
    public String newProduct(@PathVariable String catalogId, @PathVariable String categoryId, Model model) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition categoryDefinition = productCatalog.findCategoryDefinition(categoryId);

        Product product = new Product(categoryDefinition.getCategoryId(), categoryDefinition.getCategory());
        return showEditProductForm( model,catalogId, product, categoryDefinition);
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products")
    public String showProducts(@PathVariable String catalogId, @PathVariable String categoryId,
                               @RequestParam(value = "status", defaultValue = "Enabled") String status,
                               @RequestParam(required = false, defaultValue = "1") int page,
                               @RequestParam(required = false) String brand,
                               @RequestParam(required = false) String label,
                               @RequestParam(required = false) String pimId,
                               @RequestParam(required = false) String ean,
                               @RequestParam(required = false) String mfn,
                               Model model) {
        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition categoryDefinition = productCatalog.findCategoryDefinition(categoryId);

        List<Product> products = new LinkedList<>();
        if (categoryDefinition.hasType(CategoryDefinitionType.Dynamic)) {
            InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(getStoreId());

            if ("Enabled".equalsIgnoreCase(status)) {
                products = recommendationEngine.getRecommendationsForMappedProducts(categoryDefinition, enabledInventory).stream()
                        .map(ProductRecommendation::toProduct)
                        .collect(Collectors.toList());
            } else if ("Disabled".equalsIgnoreCase(status)) {
                products = recommendationEngine.getRecommendationsForUnmappedProducts(categoryDefinition, enabledInventory).stream()
                        .map(ProductRecommendation::toProduct)
                        .collect(Collectors.toList());
            } else if ("Queued".equalsIgnoreCase(status)) {
                products = recommendationEngine.getRecommendations(categoryDefinition, enabledInventory).stream()
                        .filter(r -> !r.hasPimId())
                        .map(ProductRecommendation::toProduct)
                        .collect(Collectors.toList());
            } else if ("MarketplaceEligible".equalsIgnoreCase(status)) {
                if (!categoryDefinition.hasEnabledMarketplaceDefinitions()) {
                    products = new LinkedList<>();
                } else {
                    products = recommendationEngine.getRecommendationsForMappedProducts(categoryDefinition, enabledInventory).stream()
                            .map(ProductRecommendation::toProduct)
                            .collect(Collectors.toList());
                }
            } else if ("ExpectedStock".equalsIgnoreCase(status)) {
                // function not applicable for dynamic categories
                products = new LinkedList<>();
            }
            List<Product> sortedProducts = products.stream()
                    .sorted(Comparator.comparing(Product::getLabel, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Product::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                    .filter(p -> !isNotBlank(brand) || StringUtils.equalsIgnoreCase(p.getBrand(), brand))
                    .filter(r -> !isNotBlank(label) || StringUtils.equalsIgnoreCase(r.getLabel(), label))
                    .filter(p -> !isNotBlank(pimId) || StringUtils.equalsIgnoreCase(p.getPimId(), pimId))
                    .filter(p -> !isNotBlank(ean) || StringUtils.equalsIgnoreCase(p.getEan(), ean))
                    .filter(p -> !isNotBlank(mfn) || StringUtils.equalsIgnoreCase(p.getManufacturerCode(), mfn))
                    .collect(Collectors.toList());

            products = PaginationUtil.paginate(sortedProducts, page, PRODUCTS_PAGE_SIZE, model);
            model.addAttribute("products", products);
        } else {
            Boolean enabled = null;
            if ("Enabled".equalsIgnoreCase(status)) {
                enabled = true;
            } else if ("Disabled".equalsIgnoreCase(status)) {
                enabled = false;
            }

            if ("MarketplaceEligible".equalsIgnoreCase(status)) {
                if (!categoryDefinition.hasEnabledMarketplaceDefinitions()) {
                    products = new LinkedList<>();
                } else {
                    boolean hasExportSelectedProducts = categoryDefinition.getMarketplaceDefinitions().stream()
                            .allMatch(MarketplaceDefinition::isExportSelectedProducts);

                    if (hasExportSelectedProducts) {
                        products = productRepository.findAllProductsPaginated(
                                categoryDefinition.getCategoryId(), true, brand, label, pimId, ean, mfn, true, null, true, null, page, PRODUCTS_PAGE_SIZE);
                    } else {
                        products = productRepository.findAllProductsPaginated(
                                categoryDefinition.getCategoryId(), true, brand, label, pimId, ean, mfn, true, null, null, null, page, PRODUCTS_PAGE_SIZE);
                    }
                }
                model.addAttribute("products", products.subList(0, Math.min(products.size(), PRODUCTS_PAGE_SIZE)));
                model.addAttribute("currentPage", page);
                model.addAttribute("hasNextPage", products.size() > PRODUCTS_PAGE_SIZE);
            } else if ("ExpectedStock".equalsIgnoreCase(status)) {
                products = productRepository.findAllProductsPaginated(categoryDefinition.getCategoryId(), null, brand, label, pimId, ean, mfn, true, 1, null, null, page, PRODUCTS_PAGE_SIZE);
                model.addAttribute("products", products.subList(0, Math.min(products.size(), PRODUCTS_PAGE_SIZE)));
                model.addAttribute("currentPage", page);
                model.addAttribute("hasNextPage", products.size() > PRODUCTS_PAGE_SIZE);
            } else if ("SuggestedRetailPrice".equalsIgnoreCase(status)) {
                products = productRepository.findAllProductsPaginated(categoryDefinition.getCategoryId(), null, brand, label, pimId, ean, mfn, true, null, null, 1, page, PRODUCTS_PAGE_SIZE);
                model.addAttribute("products", products.subList(0, Math.min(products.size(), PRODUCTS_PAGE_SIZE)));
                model.addAttribute("currentPage", page);
                model.addAttribute("hasNextPage", products.size() > PRODUCTS_PAGE_SIZE);
            } else {
                products = productRepository.findAllProductsPaginated(categoryDefinition.getCategoryId(), enabled, brand, label, pimId, ean, mfn, !"Queued".equalsIgnoreCase(status), null, null, null, page, PRODUCTS_PAGE_SIZE);
                model.addAttribute("products", products.subList(0, Math.min(products.size(), PRODUCTS_PAGE_SIZE)));
                model.addAttribute("currentPage", page);
                model.addAttribute("hasNextPage", products.size() > PRODUCTS_PAGE_SIZE);
            }
        }

        model.addAttribute("catalogId", catalogId);
        model.addAttribute("categoryDefinition", categoryDefinition);

        Map<String, Object> paginationParams = new HashMap<>();
        if (StringUtils.isNotBlank(status)) paginationParams.put("status", status);
        if (StringUtils.isNotBlank(brand)) paginationParams.put("brand", brand);
        if (StringUtils.isNotBlank(pimId)) paginationParams.put("pimId", pimId);
        if (StringUtils.isNotBlank(ean)) paginationParams.put("ean", ean);
        if (StringUtils.isNotBlank(mfn)) paginationParams.put("mfn", mfn);
        model.addAttribute("paginationParams", paginationParams);

        Set<String> unmappedLabels = new HashSet<>();
        if (categoryDefinition.hasGrouping()) {
            unmappedLabels = products.stream()
                    .map(Product::getLabel)
                    .filter(l -> !categoryDefinition.getGroupingOrder().contains(l))
                    .collect(Collectors.toSet());
        }
        model.addAttribute("unmappedLabels", unmappedLabels);

        Set<String> quickFilters = products.stream()
                .map(Product::getQuickFilters)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        model.addAttribute("quickFilters", quickFilters);

        Collection<String> customAttributesFilters = products.stream()
                .map(Product::getCustomAttributesFilters)
                .flatMap(List::stream)
                .map(filter -> {

                    String valueType = guessValueType(filter.getValue());
                    if (valueType.equalsIgnoreCase("text")) {
                        return filter.getCategory() + ":" + filter.getName() + " " + filter.getOperator() + " " + filter.getValue();
                    }
                    return filter.getCategory() + ":" + filter.getName() + " " + filter.getOperator() + " " + valueType;

                })
                .distinct().sorted().collect(Collectors.toList());
        model.addAttribute("customAttributesFilters", customAttributesFilters);

        Collection<String> customAttributes = products.stream()
                .map(Product::getCustomAttributes)
                .flatMap(List::stream)
                .map(tag -> {

                    String valueType = guessValueType(tag.getValue());
                    if (valueType.equalsIgnoreCase("text")) {
                        return tag.getName() + ": " + tag.getValue();
                    }
                    return tag.getName() + ": " + valueType;

                })
                .distinct().sorted().collect(Collectors.toList());
        model.addAttribute("customAttributes", customAttributes);

        Collection<String> metadata = products.stream()
                .map(Product::getMetadata)
                .flatMap(List::stream)
                        .map(m -> {

                            String valueType = guessValueType(m.getValue());
                            if (valueType.equalsIgnoreCase("text")) {
                                return m.getKey() + ": " + m.getValue();
                            }
                            return m.getKey() + ": " + valueType;

                        })
                .distinct().sorted().collect(Collectors.toList());
        model.addAttribute("metadataTags", metadata);

        Collection<String> brands = asDistinctCollectionFromStream(products.stream()
                .map(Product::getBrand)
                .filter(b -> !Objects.isNull(b)));
        model.addAttribute("brands", brands);

        return "catalogDetails_categoryDefinition_productsList";
    }

    @GetMapping("/dashboard/catalogs/{catalogId}/category/{categoryId}/products/{ean}/new")
    public String newProduct(@PathVariable String catalogId, @PathVariable String categoryId, @PathVariable String ean, Model model) {
        MatchedInventory matchedInventory = inventory.withEnabledSuppliersOnly(getStoreId()).findByEan(ean);
        if (matchedInventory.isEmpty()) {
            throw new RuntimeException("Product not found in inventory");
        }

        ProductCatalog productCatalog = productCatalogRepository.findById(getStoreId(), catalogId);
        CategoryDefinition categoryDefinition = productCatalog.findCategoryDefinition(categoryId);

        InventoryKey key = matchedInventory.getInventoryKey();
        Optional<PimEntry> pimEntry = pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());

        ProductRecommendation recommendation = new ProductRecommendation(categoryDefinition, matchedInventory, pimEntry);
        return showEditProductForm(model, catalogId, recommendation.toProduct(), categoryDefinition);
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

        model.addAttribute("productCategories", store.getEnabledProductCategories());
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
                            product.setBrand(entry.brand());
                        });
            }
        }

        // Check if EAN requires PIM ID change
        validateIfPimChangeIsRequired(product, existingProduct);

        existingProduct.setLabel(product.getLabel());
        existingProduct.setName(product.getName());
        existingProduct.setRecommendation(product.getRecommendation());
        existingProduct.setEnabled(product.isEnabled());

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
