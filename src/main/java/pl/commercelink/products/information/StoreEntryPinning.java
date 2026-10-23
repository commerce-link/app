package pl.commercelink.products.information;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.CategoryDefinition;
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
@Slf4j
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
        List<String> ownerCategoryIds = categoryIdsOf(ownerStoreId);
        Codes announced = Codes.of(event.eans(), event.mfnCodes());
        PimView storeView = catalog.withStoreData(ownerStoreId);
        // the codes before the event: the copy holds them until its next refresh, and a refresh triggered meanwhile by
        // another event leaves them only in the history
        Codes inCopy = storeView.findByPimId(event.pimId())
                .map(entry -> Codes.of(entry.gtins(), entry.mpns()))
                .orElse(Codes.EMPTY);
        Codes before = inCopy.plus(Codes.of(codeHistory.eansOf(event.pimId()), codeHistory.mpnsOf(event.pimId())));

        int unpinned = detachProductsOfRemovedCodes(event.pimId(), ownerStoreId, announced, inCopy, before,
                ownerCategoryIds, catalog);
        int pinned = attachMatchingProducts(event.pimId(), ownerStoreId, announced, storeView, ownerCategoryIds);
        // one line per event, also when nothing changed: with two instances, it shows which one handled the event and
        // whether that instance knew the codes the edit replaced (a product pinned by an unknown code stays pinned)
        log.info("PIM entry {} of store {} with EANs {} and MPNs {}: {} products unpinned, {} pinned or moved here{}",
                event.pimId(), ownerStoreId, announced.eans(), announced.mfnCodes(), unpinned, pinned,
                before.isEmpty() ? "; its codes before this event are unknown to this instance (a new entry, or one "
                        + "created or changed after this instance last loaded the PIM index)" : "");
    }

    void onEntryDeleted(String pimId, PimCatalog catalog) {
        List<Product> detached = productRepository.detachPimFromProducts(pimId);
        if (detached.isEmpty()) {
            return;
        }
        PimView globalView = catalog.withGlobalData();
        detached.forEach(product -> pinToGlobalEntry(product, pimId, globalView).ifPresentOrElse(
                // detachPimFromProducts already cleared the pin: the deleted entry is the old one
            globalPimId -> repin(product, pimId, globalPimId, pimId, "entry deleted, pinned to a global entry"),
                () -> log.info("PIM entry {} deleted: product {}/{} unpinned", pimId, product.getCategoryId(),
                        product.getProductId())));
    }

    // codes known to neither the copy nor the history unpin nothing: the product stays pinned
    private int detachProductsOfRemovedCodes(String pimId, String ownerStoreId, Codes announced, Codes inCopy,
                                             Codes before, List<String> ownerCategoryIds, PimCatalog catalog) {
        Codes removed = before.minus(announced);
        if (removed.isEmpty()) {
            return 0;
        }
        log.info("PIM entry {} of store {}: codes removed by the edit {} {} (only in the history: {} {})", pimId,
                ownerStoreId, removed.eans(), removed.mfnCodes(), removed.minus(inCopy).eans(),
                removed.minus(inCopy).mfnCodes());
        Set<String> categoryIds = Set.copyOf(ownerCategoryIds);
        List<Product> toDetach = productRepository.findAllByPimId(pimId).stream()
                .filter(projection -> categoryIds.contains(projection.getCategoryId()))
                .map(projection -> productRepository.findByProductId(projection.getCategoryId(), projection.getProductId()))
                .filter(Objects::nonNull)
                .filter(product -> removed.matches(product) && !announced.matches(product))
                .toList();
        if (toDetach.isEmpty()) {
            return 0;
        }
        PimView globalView = catalog.withGlobalData();
        toDetach.forEach(product -> repin(product, product.getPimId(),
                pinToGlobalEntry(product, pimId, globalView).orElse(null), pimId, "its code was removed from the entry"));
        return toDetach.size();
    }

    private int attachMatchingProducts(String pimId, String ownerStoreId, Codes announced, PimView storeView,
                                       List<String> ownerCategoryIds) {
        List<Product> toAttach = productRepository
                .findAllByEanOrMfnInCategories(ownerCategoryIds, announced.eanList(), announced.mfnList()).stream()
                .filter(product -> isBlank(product.getPimId())
                        || isOtherEntryOfSameStore(product.getPimId(), pimId, ownerStoreId, storeView))
                .toList();
        toAttach.forEach(product -> repin(product, product.getPimId(), pimId, pimId, isBlank(product.getPimId())
                ? "its code matches the entry" : "its code moved here from another entry of the store"));
        return toAttach.size();
    }

    // every pin change is logged, after the save: the app keeps no other trace of why a product was pinned,
    // unpinned or moved, and a save that fails (the event is redelivered) must not leave a change that did not happen
    private void repin(Product product, String oldPimId, String newPimId, String handledPimId, String reason) {
        product.setPimId(newPimId);
        productRepository.save(product);
        log.info("PIM entry {}: product {}/{} pin {} -> {} ({})", handledPimId, product.getCategoryId(),
                product.getProductId(), oldPimId, newPimId, reason);
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

    // a store's own entry only ever pins products in the categories of that store's catalogs
    private List<String> categoryIdsOf(String storeId) {
        return productCatalogRepository.findAll(storeId).stream()
                .flatMap(catalog -> catalog.getCategories().stream())
                .map(CategoryDefinition::getCategoryId)
                .distinct()
                .toList();
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
