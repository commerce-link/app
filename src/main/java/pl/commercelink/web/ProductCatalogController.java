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
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.products.*;
import pl.commercelink.products.brand.BrandMapper;
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
    private PimCatalog pimCatalog;

    @Autowired
    private PimCategoryOptions pimCategoryOptions;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private BrandMapper brandMapper;

    @Value("${application.env}")
    private String env;

    @Autowired
    private MessageSource messageSource;

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

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
