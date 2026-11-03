package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.marketplace.links.OfferEans;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.web.orders.LinkOfferProduct;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The products "Link to product" offers for one catalog: every product of the catalog with an EAN and a manufacturer
 * code, what a link needs to recognise an order line and fill its item (a pimId is not required). The pricelist only
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
        Map<String, String> categoryNames = new HashMap<>();
        for (CategoryDefinition category : catalog.getCategories()) {
            if (category.getCategoryId() != null) {
                categoryNames.putIfAbsent(category.getCategoryId(), category.getName());
            }
        }
        Map<String, Long> pricesByPimId = new HashMap<>();
        Map<String, Long> pricesByEan = new HashMap<>();
        for (AvailabilityAndPrice row : pricelistFinder.availabilityAndPrices(storeId, catalogId)) {
            if (row.getPimId() != null) {
                pricesByPimId.putIfAbsent(row.getPimId(), row.getPrice());
            }
            if (StringUtils.isNotBlank(row.getEan())) {
                pricesByEan.putIfAbsent(OfferEans.unify(row.getEan()), row.getPrice());
            }
        }
        return productRepository.findAll(catalog).stream()
                .filter(product -> StringUtils.isNotBlank(product.getEan())
                        && StringUtils.isNotBlank(product.getManufacturerCode()))
                .map(product -> new LinkOfferProduct(product.getCategoryId(), product.getProductId(), product.getEan(),
                        product.getName(), product.getLabel(), product.getManufacturerCode(),
                        categoryNames.get(product.getCategoryId()),
                        StringUtils.isNotBlank(product.getPimId()) ? pricesByPimId.get(product.getPimId())
                                : pricesByEan.get(OfferEans.unify(product.getEan()))))
                .sorted(Comparator.comparing(LinkOfferProduct::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
