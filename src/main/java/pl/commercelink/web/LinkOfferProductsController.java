package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.externaloffers.OfferEans;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.web.orders.LinkOfferProduct;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The products "Link to product" offers for one catalog: every product of its manual categories with an EAN, which is all a link
 * needs to recognise an order line and fill its item (neither a manufacturer code nor a pimId is required). The pricelist only
 * carries available products, so a product without supplier stock or added after the last pricelist run would be
 * missing from it; it supplies the price where it has one, by pimId, or by EAN for a product without one.
 */
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class LinkOfferProductsController extends BaseController {

    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final PricelistFinder pricelistFinder;

    @GetMapping("/dashboard/orders/link-offer/products")
    @ResponseBody
    public List<LinkOfferProduct> products(@RequestParam String catalogId) {
        String storeId = getStoreId();
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        if (catalog == null) {
            return List.of();
        }
        // Manual categories only: an automatic one keeps the products of its manual days just for a return to manual.
        Map<String, String> manualCategoryNames = new HashMap<>();
        for (CategoryDefinition category : catalog.getCategories()) {
            if (category.getCategoryId() != null && !category.hasType(CategoryDefinitionType.Dynamic)) {
                manualCategoryNames.putIfAbsent(category.getCategoryId(), category.getName());
            }
        }
        Pricelist pricelist = pricelistFinder.newestPricelist(storeId, catalogId);
        return productRepository.findAll(catalog).stream()
                .filter(product -> manualCategoryNames.containsKey(product.getCategoryId()))
                // a product without a valid EAN would give a link the import skips
                .filter(product -> OfferEans.unifyValid(product.getEan()) != null)
                .map(product -> new LinkOfferProduct(product.getCategoryId(), product.getProductId(), product.getEan(),
                        product.getName(), product.getLabel(), product.getManufacturerCode(),
                        manualCategoryNames.get(product.getCategoryId()),
                        (StringUtils.isNotBlank(product.getPimId()) ? pricelist.findByPimId(product.getPimId())
                                : pricelist.findByEan(product.getEan()))
                                .map(AvailabilityAndPrice::getPrice).orElse(null)))
                .sorted(Comparator.comparing(LinkOfferProduct::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
