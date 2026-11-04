package pl.commercelink.marketplace.links;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.links.ResolvedOffer.Source;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Resolves a marketplace order line to a product: an explicit offer link first (its EANs looked up among the products
 * of the store's catalogs), then the seller offer key taken as our own pimId, then the manufacturer code as MPN (the
 * behaviour before offer links existed). The product's EAN is what fulfilment looks it up by; see
 * {@link ResolvedOffer#sku()}.
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

    public class StoreOffers {

        private final String storeId;
        private Map<String, String> categoryNamesById;
        private List<Product> catalogProducts;
        private Map<String, List<Integer>> catalogPositionsByEan;

        private StoreOffers(String storeId) {
            this.storeId = storeId;
        }

        public Optional<ResolvedOffer> resolve(String marketplace, String sellerOfferKey, String manufacturerCode) {
            if (StringUtils.isNotBlank(sellerOfferKey)) {
                Optional<ResolvedOffer> linked = linkRepository.find(storeId, marketplace, sellerOfferKey)
                        .flatMap(link -> fromLink(link, sellerOfferKey));
                if (linked.isPresent()) {
                    return linked;
                }
                Optional<ResolvedOffer> own = pimAliases.effectiveEntry(storeId, sellerOfferKey)
                        .map(entry -> fromEntry(entry, Source.OWN_OFFER))
                        .filter(offer -> hasProductCode(offer, sellerOfferKey));
                if (own.isPresent()) {
                    return own;
                }
            }

            if (StringUtils.isNotBlank(manufacturerCode)) {
                return pimCatalog.withStoreData(storeId).findByMpn(manufacturerCode)
                        .map(entry -> new ResolvedOffer(entry.pimId(), OfferEans.unify(entry.primaryGtin().orElse(null)),
                                manufacturerCode, entry.category(), Source.MFN));
            }
            return Optional.empty();
        }

        /**
         * Every product of the store's catalogs, read once per scope, in a stable order: catalogs by id, categories in
         * each catalog's order, products of a category by id.
         */
        public List<Product> catalogProducts() {
            if (catalogProducts == null) {
                catalogProducts = categoryNamesById().keySet().stream()
                        .flatMap(categoryId -> productRepository.findAll(categoryId).stream()
                                .sorted(Comparator.comparing(Product::getProductId, Comparator.nullsLast(Comparator.naturalOrder()))))
                        .toList();
            }
            return catalogProducts;
        }

        /**
         * Which product of the store's catalogs the EANs stand for. Hits sharing one EAN are one product (the same goods
         * in several categories, or listed twice), so the first of them in {@link #catalogProducts()} order wins.
         */
        public CatalogMatch matchCatalog(Collection<String> eans) {
            Set<String> unified = new LinkedHashSet<>();
            if (eans != null) {
                eans.stream().map(OfferEans::unify).filter(Objects::nonNull).forEach(unified::add);
            }
            if (unified.isEmpty()) {
                return new CatalogMatch.NotInCatalog();
            }
            TreeMap<Integer, Product> hitsInCatalogOrder = new TreeMap<>();
            for (String ean : unified) {
                for (int position : catalogPositionsByEan().getOrDefault(ean, List.of())) {
                    hitsInCatalogOrder.put(position, catalogProducts().get(position));
                }
            }
            if (hitsInCatalogOrder.isEmpty()) {
                return new CatalogMatch.NotInCatalog();
            }
            List<Product> hits = List.copyOf(hitsInCatalogOrder.values());
            long distinctEans = hits.stream().map(product -> OfferEans.unify(product.getEan())).distinct().count();
            if (distinctEans > 1) {
                return new CatalogMatch.Ambiguous(hits);
            }
            Product first = hits.get(0);
            return new CatalogMatch.Found(first, categoryNamesById().get(first.getCategoryId()));
        }

        // without a code the item would carry the marketplace's raw code as its sku while looking resolved
        private boolean hasProductCode(ResolvedOffer offer, String sellerOfferKey) {
            if (offer.sku() != null) {
                return true;
            }
            log.warn("Offer {} of store {} matches {} ({}), which has neither an EAN nor a manufacturer code; treated as"
                    + " unresolved", sellerOfferKey, storeId, offer.pimId(), offer.source());
            return false;
        }

        private Optional<ResolvedOffer> fromLink(MarketplaceOfferLink link, String sellerOfferKey) {
            return switch (matchCatalog(link.getEans())) {
                case CatalogMatch.Found found -> Optional.of(new ResolvedOffer(found.product().getPimId(),
                        OfferEans.unify(found.product().getEan()), found.product().getManufacturerCode(), found.categoryName(), Source.LINK));
                case CatalogMatch.Ambiguous ambiguous -> {
                    log.warn("Offer {} of store {} matches {} catalog products with different EANs; treated as unresolved",
                            sellerOfferKey, storeId, ambiguous.products().size());
                    yield Optional.empty();
                }
                case CatalogMatch.NotInCatalog notInCatalog -> Optional.empty();
            };
        }

        // positions in catalogProducts(), so hits of several EANs can be put back in catalog order
        private Map<String, List<Integer>> catalogPositionsByEan() {
            if (catalogPositionsByEan == null) {
                Map<String, List<Integer>> index = new HashMap<>();
                List<Product> all = catalogProducts();
                for (int position = 0; position < all.size(); position++) {
                    String ean = OfferEans.unify(all.get(position).getEan());
                    if (ean != null) {
                        index.computeIfAbsent(ean, key -> new ArrayList<>()).add(position);
                    }
                }
                catalogPositionsByEan = index;
            }
            return catalogPositionsByEan;
        }

        private ResolvedOffer fromEntry(PimEntry entry, Source source) {
            Optional<Product> product = catalogProduct(entry.pimId());
            String ean = OfferEans.unify(product.map(Product::getEan).filter(StringUtils::isNotBlank)
                    .orElseGet(() -> entry.primaryGtin().orElse(null)));
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
                // insertion-ordered: it fixes which category of a product found twice counts as first
                Map<String, String> names = new LinkedHashMap<>();
                productCatalogRepository.findAll(storeId).stream()
                        .sorted(Comparator.comparing(ProductCatalog::getCatalogId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .flatMap(catalog -> catalog.getCategories().stream())
                        .forEach(category -> names.putIfAbsent(category.getCategoryId(), category.getName()));
                categoryNamesById = names;
            }
            return categoryNamesById;
        }
    }
}
