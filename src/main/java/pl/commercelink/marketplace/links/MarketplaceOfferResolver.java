package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.links.ResolvedOffer.Source;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a marketplace order line to a product: an explicit offer link first, then the seller offer key taken as our
 * own pimId, then the manufacturer code as MPN (the behaviour before offer links existed).
 */
@Component
public class MarketplaceOfferResolver {

    private final MarketplaceOfferLinkRepository linkRepository;
    private final PimCatalog pimCatalog;
    private final StorePimAliases pimAliases;
    private final ProductRepository productRepository;
    private final ProductCatalogRepository productCatalogRepository;

    public MarketplaceOfferResolver(MarketplaceOfferLinkRepository linkRepository, PimCatalog pimCatalog,
                                    StorePimAliases pimAliases, ProductRepository productRepository,
                                    ProductCatalogRepository productCatalogRepository) {
        this.linkRepository = linkRepository;
        this.pimCatalog = pimCatalog;
        this.pimAliases = pimAliases;
        this.productRepository = productRepository;
        this.productCatalogRepository = productCatalogRepository;
    }

    public Optional<ResolvedOffer> resolve(String storeId, String marketplace, String sellerOfferKey, String manufacturerCode) {
        PimView view = pimCatalog.withStoreData(storeId);

        if (StringUtils.isNotBlank(sellerOfferKey)) {
            Optional<ResolvedOffer> linked = linkRepository.find(storeId, marketplace, sellerOfferKey)
                    .flatMap(link -> entryOf(view, storeId, link.getPimId()))
                    .map(entry -> fromEntry(storeId, entry, Source.LINK));
            if (linked.isPresent()) {
                return linked;
            }
            Optional<ResolvedOffer> own = entryOf(view, storeId, sellerOfferKey)
                    .map(entry -> fromEntry(storeId, entry, Source.OWN_OFFER));
            if (own.isPresent()) {
                return own;
            }
        }

        if (StringUtils.isNotBlank(manufacturerCode)) {
            return view.findByMpn(manufacturerCode)
                    .map(entry -> new ResolvedOffer(entry.pimId(), entry.primaryGtin().orElse(null), manufacturerCode,
                            entry.category(), Source.MFN));
        }
        return Optional.empty();
    }

    /** Whether a product of the store's catalogs carries this pimId (what an operator may link an offer to). */
    public boolean isInStoreCatalog(String storeId, String pimId) {
        return catalogProduct(storeId, pimId).isPresent();
    }

    // a store's clone replaces its source for that store, so it wins over the source entry
    private Optional<PimEntry> entryOf(PimView view, String storeId, String pimId) {
        if (StringUtils.isBlank(pimId)) {
            return Optional.empty();
        }
        Optional<PimEntry> clone = pimAliases.cloneOf(storeId, pimId).flatMap(view::findByPimId);
        return clone.isPresent() ? clone : view.findByPimId(pimId);
    }

    private ResolvedOffer fromEntry(String storeId, PimEntry entry, Source source) {
        Optional<Product> product = catalogProduct(storeId, entry.pimId());
        String ean = product.map(Product::getEan).filter(StringUtils::isNotBlank)
                .orElseGet(() -> entry.primaryGtin().orElse(null));
        String mfn = product.map(Product::getManufacturerCode).filter(StringUtils::isNotBlank)
                .orElseGet(() -> entry.primaryMpn().orElse(null));
        return new ResolvedOffer(entry.pimId(), ean, mfn, entry.category(), source);
    }

    private Optional<Product> catalogProduct(String storeId, String pimId) {
        Set<String> categoryIds = productCatalogRepository.findAll(storeId).stream()
                .flatMap(catalog -> catalog.getCategories().stream())
                .map(CategoryDefinition::getCategoryId)
                .collect(Collectors.toSet());
        return productRepository.findAllByPimId(pimId).stream()
                .filter(projection -> categoryIds.contains(projection.getCategoryId()))
                .map(projection -> productRepository.findByProductId(projection.getCategoryId(), projection.getProductId()))
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }
}
