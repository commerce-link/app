package pl.commercelink.products.information;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Keeps product pins in step with a store's own PIM entry whose codes changed, and with any deleted entry.
 * Only the codes an edit removed can unpin a product: products are often pinned through a sibling code of their
 * supplier group, so "own code not in the entry" alone would unpin them wrongly.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class StoreEntryPinning {

    private final ProductRepository productRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final StoreEntryCodeHistory codeHistory;

    void onStoreEntryAnnounced(PIMEntryAddedEvent event, PimCatalog catalog) {
        String ownerStoreId = event.ownerStoreId();
        if (ownerStoreId == null) {
            return;
        }
        List<String> ownerCategoryIds = PimCatalogRegistry.categoryIdsOf(ownerStoreId, productCatalogRepository);
        Codes announced = Codes.of(event.eans(), event.mfnCodes());
        PimView storeView = catalog.withStoreData(ownerStoreId);

        detachProductsOfRemovedCodes(event.pimId(), announced, storeView, ownerCategoryIds, catalog);
        attachMatchingProducts(event.pimId(), ownerStoreId, announced, storeView, ownerCategoryIds);
    }

    void onEntryDeleted(String pimId, PimCatalog catalog) {
        List<Product> detached = productRepository.detachPimFromProducts(pimId);
        if (detached.isEmpty()) {
            return;
        }
        PimView globalView = catalog.withGlobalData();
        detached.forEach(product -> pinToGlobalEntry(product, pimId, globalView).ifPresent(globalPimId -> {
            product.setPimId(globalPimId);
            productRepository.save(product);
        }));
    }

    private void detachProductsOfRemovedCodes(String pimId, Codes announced, PimView storeView,
                                              List<String> ownerCategoryIds, PimCatalog catalog) {
        // the codes before the edit: the copy holds them until its next refresh, and a refresh triggered meanwhile by
        // another event leaves them only in the history; codes known to neither unpin nothing (the product stays pinned)
        Codes before = storeView.findByPimId(pimId)
                .map(entry -> Codes.of(entry.gtins(), entry.mpns()))
                .orElse(Codes.EMPTY)
                .plus(Codes.of(codeHistory.eansOf(pimId), codeHistory.mpnsOf(pimId)));
        Codes removed = before.minus(announced);
        if (removed.isEmpty()) {
            return;
        }
        Set<String> categoryIds = Set.copyOf(ownerCategoryIds);
        PimView globalView = catalog.withGlobalData();
        productRepository.findAllByPimId(pimId).stream()
                .filter(projection -> categoryIds.contains(projection.getCategoryId()))
                .map(projection -> productRepository.findByProductId(projection.getCategoryId(), projection.getProductId()))
                .filter(Objects::nonNull)
                .filter(product -> removed.matches(product) && !announced.matches(product))
                .forEach(product -> {
                    product.setPimId(pinToGlobalEntry(product, pimId, globalView).orElse(null));
                    productRepository.save(product);
                });
    }

    private void attachMatchingProducts(String pimId, String ownerStoreId, Codes announced, PimView storeView,
                                        List<String> ownerCategoryIds) {
        productRepository.findAllByEanOrMfnInCategories(ownerCategoryIds, announced.eanList(), announced.mfnList())
                .stream()
                .filter(product -> isBlank(product.getPimId())
                        || isOtherEntryOfSameStore(product.getPimId(), pimId, ownerStoreId, storeView))
                .forEach(product -> {
                    product.setPimId(pimId);
                    productRepository.save(product);
                });
    }

    // a code belongs to one entry within a store, so a product of the store on another of its entries moves here;
    // products pinned to a global entry stay where they are
    private static boolean isOtherEntryOfSameStore(String currentPimId, String pimId, String ownerStoreId,
                                                   PimView storeView) {
        if (currentPimId.equals(pimId)) {
            return false;
        }
        return storeView.findByPimId(currentPimId)
                .filter(entry -> !entry.global() && ownerStoreId.equals(entry.ownerStoreId()))
                .isPresent();
    }

    // the handled entry may still sit in the copy of the index, so it is never the result
    private static Optional<String> pinToGlobalEntry(Product product, String handledPimId, PimView globalView) {
        return globalView.findByGtinOrMpn(product.getEan(), product.getManufacturerCode())
                .map(PimEntry::pimId)
                .filter(globalPimId -> !globalPimId.equals(handledPimId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Codes(Set<String> eans, Set<String> mfnCodes) {

        static final Codes EMPTY = new Codes(Set.of(), Set.of());

        static Codes of(Collection<String> eans, Collection<String> mfnCodes) {
            return new Codes(unified(eans, UnifiedProductIdentifiers::unifyEan),
                    unified(mfnCodes, UnifiedProductIdentifiers::unifyMfn));
        }

        private static Set<String> unified(Collection<String> codes, UnaryOperator<String> unify) {
            if (codes == null) {
                return Set.of();
            }
            return codes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(unify)
                    .collect(Collectors.toUnmodifiableSet());
        }

        Codes plus(Codes other) {
            Set<String> allEans = new HashSet<>(eans);
            allEans.addAll(other.eans);
            Set<String> allMfnCodes = new HashSet<>(mfnCodes);
            allMfnCodes.addAll(other.mfnCodes);
            return new Codes(allEans, allMfnCodes);
        }

        Codes minus(Codes other) {
            Set<String> remainingEans = new HashSet<>(eans);
            remainingEans.removeAll(other.eans);
            Set<String> remainingMfnCodes = new HashSet<>(mfnCodes);
            remainingMfnCodes.removeAll(other.mfnCodes);
            return new Codes(remainingEans, remainingMfnCodes);
        }

        boolean isEmpty() {
            return eans.isEmpty() && mfnCodes.isEmpty();
        }

        boolean matches(Product product) {
            String ean = product.getEan();
            String mfn = product.getManufacturerCode();
            return (ean != null && eans.contains(ean)) || (mfn != null && mfnCodes.contains(mfn));
        }

        List<String> eanList() {
            return List.copyOf(eans);
        }

        List<String> mfnList() {
            return List.copyOf(mfnCodes);
        }
    }
}
