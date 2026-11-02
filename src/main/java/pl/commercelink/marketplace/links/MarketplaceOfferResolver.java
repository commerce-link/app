package pl.commercelink.marketplace.links;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.links.ResolvedOffer.Source;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a marketplace order line to a product: an explicit offer link first, then the seller offer key taken as our
 * own pimId, then the manufacturer code as MPN (the behaviour before offer links existed).
 */
@Slf4j
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

    /**
     * Resolutions for the lines of one order (or one operator action): the store's catalogs are read at most once,
     * and only when a line needs them. Not meant to outlive that call, so catalog changes are seen by the next one.
     */
    public StoreOffers forStore(String storeId) {
        return new StoreOffers(storeId);
    }

    public Optional<ResolvedOffer> resolve(String storeId, String marketplace, String sellerOfferKey, String manufacturerCode) {
        return forStore(storeId).resolve(marketplace, sellerOfferKey, manufacturerCode);
    }

    /** Whether a product of the store's catalogs carries this pimId (what an operator may link an offer to). */
    public boolean isInStoreCatalog(String storeId, String pimId) {
        return forStore(storeId).isInStoreCatalog(pimId);
    }

    public class StoreOffers {

        private final String storeId;
        private Map<String, String> categoryNamesById;
        private List<Product> catalogProducts;

        private StoreOffers(String storeId) {
            this.storeId = storeId;
        }

        public Optional<ResolvedOffer> resolve(String marketplace, String sellerOfferKey, String manufacturerCode) {
            if (StringUtils.isNotBlank(sellerOfferKey)) {
                Optional<ResolvedOffer> linked = linkRepository.find(storeId, marketplace, sellerOfferKey)
                        .flatMap(link -> pimAliases.effectiveEntry(storeId, link.getPimId()))
                        .map(entry -> fromEntry(entry, Source.LINK))
                        .filter(offer -> hasManufacturerCode(offer, sellerOfferKey));
                if (linked.isPresent()) {
                    return linked;
                }
                Optional<ResolvedOffer> own = pimAliases.effectiveEntry(storeId, sellerOfferKey)
                        .map(entry -> fromEntry(entry, Source.OWN_OFFER))
                        .filter(offer -> hasManufacturerCode(offer, sellerOfferKey));
                if (own.isPresent()) {
                    return own;
                }
            }

            if (StringUtils.isNotBlank(manufacturerCode)) {
                return pimCatalog.withStoreData(storeId).findByMpn(manufacturerCode)
                        .map(entry -> new ResolvedOffer(entry.pimId(), entry.primaryGtin().orElse(null), manufacturerCode,
                                entry.category(), Source.MFN));
            }
            return Optional.empty();
        }

        /** The products of the store's catalogs that carry a pimId, read once per scope. */
        public List<Product> catalogProducts() {
            if (catalogProducts == null) {
                catalogProducts = categoryNamesById().keySet().stream()
                        .flatMap(categoryId -> productRepository.findAll(categoryId).stream())
                        .filter(product -> StringUtils.isNotBlank(product.getPimId()))
                        .toList();
            }
            return catalogProducts;
        }

        public boolean isInStoreCatalog(String pimId) {
            return catalogProduct(pimId).isPresent();
        }

        // without an MFN the item would carry the marketplace's raw code as its sku while looking resolved
        private boolean hasManufacturerCode(ResolvedOffer offer, String sellerOfferKey) {
            if (StringUtils.isNotBlank(offer.mfn())) {
                return true;
            }
            log.warn("Offer {} of store {} matches {} ({}), which has no manufacturer code; treated as unresolved",
                    sellerOfferKey, storeId, offer.pimId(), offer.source());
            return false;
        }

        private ResolvedOffer fromEntry(PimEntry entry, Source source) {
            Optional<Product> product = catalogProduct(entry.pimId());
            String ean = product.map(Product::getEan).filter(StringUtils::isNotBlank)
                    .orElseGet(() -> entry.primaryGtin().orElse(null));
            String mfn = product.map(Product::getManufacturerCode).filter(StringUtils::isNotBlank)
                    .orElseGet(() -> entry.primaryMpn().orElse(null));
            // an entry without a PIM category takes the name of its catalog category, as the store's pricelist does
            String category = StringUtils.isNotBlank(entry.category()) ? entry.category()
                    : product.map(p -> categoryNamesById().get(p.getCategoryId())).orElse(null);
            return new ResolvedOffer(entry.pimId(), ean, mfn, category, source);
        }

        // a store product may still be pinned to the clone's source, so every alias of the id is looked up
        private Optional<Product> catalogProduct(String pimId) {
            Map<String, String> categories = categoryNamesById();
            if (categories.isEmpty() || StringUtils.isBlank(pimId)) {
                return Optional.empty();
            }
            Set<String> ids = new LinkedHashSet<>();
            ids.add(pimId);
            ids.addAll(pimAliases.aliasesOf(storeId, pimId));
            return ids.stream()
                    .flatMap(id -> productRepository.findAllByPimId(id).stream())
                    .filter(projection -> categories.containsKey(projection.getCategoryId()))
                    .map(projection -> productRepository.findByProductId(projection.getCategoryId(), projection.getProductId()))
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        private Map<String, String> categoryNamesById() {
            if (categoryNamesById == null) {
                Map<String, String> names = new HashMap<>();
                productCatalogRepository.findAll(storeId).stream()
                        .flatMap(catalog -> catalog.getCategories().stream())
                        .forEach(category -> names.putIfAbsent(category.getCategoryId(), category.getName()));
                categoryNamesById = names;
            }
            return categoryNamesById;
        }
    }
}
