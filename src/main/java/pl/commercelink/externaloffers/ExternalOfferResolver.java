package pl.commercelink.externaloffers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.externaloffers.ResolvedOffer.Source;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.*;
import pl.commercelink.products.information.StorePimAliases;

import java.util.*;

/**
 * Resolves a marketplace order line to the EAN fulfilment looks goods up by: first the seller offer key taken as our own
 * pimId (an offer CommerceLink published, for which no link is ever read), then for any other key an explicit offer link
 * (its EANs, taken as they are: fulfilment finds the goods in the warehouse and the suppliers' feeds, so the product need
 * not be in a catalog; the category comes from {@link ProductDataResolver}). The line's manufacturer code is never looked
 * up: without a brand it can name another product, such as another size of the same model. See
 * {@link ResolvedOffer#sku()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalOfferResolver {

    private final ExternalOfferLinkRepository linkRepository;
    private final StorePimAliases pimAliases;
    private final ProductRepository productRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductDataResolver productDataResolver;
    private final StoreCategoryResolver storeCategoryResolver;

    /**
     * Resolutions for the lines of one import message or one operator action. What a line needs is read lazily and then
     * kept for the scope: the store's catalog list at most twice (once for the catalog categories of own offers, once
     * through {@link StoreCategoryResolver} for the category names of linked lines), the products of every catalog
     * category at most once (only {@link StoreOffers#catalogProducts()} reads them), and the product data once per EAN.
     * An own offer's product is still looked up by pimId for each line. Not meant to outlive that call, so catalog changes
     * are seen by the next one.
     */
    public StoreOffers forStore(String storeId) {
        return new StoreOffers(storeId);
    }

    /**
     * Whether the seller offer key is the pimId of an offer CommerceLink published for the store (the rule offer
     * matching counts its own offers by). Such offers are left out of offer links altogether.
     */
    public boolean isOwnOffer(String storeId, String offerKey) {
        return StringUtils.isNotBlank(offerKey) && pimAliases.effectiveEntry(storeId, offerKey).isPresent();
    }

    public Optional<ResolvedOffer> resolve(String storeId, String orderSource, String offerKey) {
        return forStore(storeId).resolve(orderSource, offerKey);
    }

    public class StoreOffers {

        private final String storeId;
        private Map<String, String> categoryNamesById;
        private List<Product> catalogProducts;
        private Map<String, String> storeCategoryNamesByPimCategoryId;
        // one import can bring the same offer in many orders
        private final Map<String, Optional<ProductData>> productByEan = new HashMap<>();

        private StoreOffers(String storeId) {
            this.storeId = storeId;
        }

        public Optional<ResolvedOffer> resolve(String orderSource, String offerKey) {
            if (isOwnOffer(storeId, offerKey)) {
                return pimAliases.effectiveEntry(storeId, offerKey)
                        .map(entry -> fromEntry(entry, Source.OWN_OFFER))
                        .filter(offer -> offer.sku() != null);
            }
            if (StringUtils.isBlank(offerKey)) {
                return Optional.empty();
            }
            return linkRepository.find(storeId, orderSource, offerKey).flatMap(this::fromLink);
        }

        /**
         * Every product of the store's catalogs, read once per scope, in a stable order: catalogs by id, categories in
         * each catalog's order, products of a category by id.
         */
        /**
         * One product of the store's catalogs, read by its key; a category that is not the store's gives nothing, so
         * another store's product cannot be picked. "Link to product" needs only the one the operator chose.
         */
        public Optional<Product> catalogProduct(String categoryId, String productId) {
            if (StringUtils.isAnyBlank(categoryId, productId) || !categoryNamesById().containsKey(categoryId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(productRepository.findByProductId(categoryId, productId));
        }

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
         * A marketplace offer is one product, so a link listing several EANs lists codes of the same goods (Empik gives
         * an EAN-13 and its 12-digit UPC form, which unify to one; a product can also carry two codes): all of them go
         * on the item, so fulfilment finds the goods under whichever code a supplier knows. A value without the format of
         * an EAN, saved before the format was checked, is skipped; a link left without any is no link.
         */
        private Optional<ResolvedOffer> fromLink(ExternalOfferLink link) {
            List<String> eans = OfferEans.validUnified(link.getEans());
            return eans.isEmpty() ? Optional.empty() : Optional.of(linked(eans));
        }

        /** What a line linked to these unified EANs gets: the EANs, and the category of the goods. */
        public ResolvedOffer linked(List<String> eans) {
            return ResolvedOffer.linked(eans, productCategory(eans));
        }

        /**
         * The category of the first EAN {@link ProductDataResolver} knows with a category (one still waiting for its
         * category is skipped), named as the store calls the PIM category in its catalogs, else by its own name; null
         * when none is known. A warehouse item takes its category the same way, from its own EAN
         * ({@code WarehouseItemFactory}).
         */
        private String productCategory(List<String> eans) {
            for (String ean : eans) {
                ProductData product = productByEan.computeIfAbsent(ean,
                        key -> productDataResolver.ofEan(storeId, key)).orElse(null);
                if (product != null && StringUtils.isNotBlank(product.category())) {
                    String storeName = StringUtils.isBlank(product.categoryId()) ? null
                            : storeCategoryNames().get(product.categoryId());
                    return StringUtils.isNotBlank(storeName) ? storeName : product.category();
                }
            }
            return null;
        }

        private Map<String, String> storeCategoryNames() {
            if (storeCategoryNamesByPimCategoryId == null) {
                storeCategoryNamesByPimCategoryId = storeCategoryResolver.categoryNamesByPimCategoryId(storeId);
            }
            return storeCategoryNamesByPimCategoryId;
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
