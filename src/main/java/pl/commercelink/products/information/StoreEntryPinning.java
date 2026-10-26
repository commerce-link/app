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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Keeps product pins in step with a store's own PIM entry whose codes changed, and with any deleted entry.
 * Only the codes an edit removed can unpin a product: products are often pinned through a sibling code of their
 * supplier group, so "own code not in the entry" alone would unpin them wrongly. A store's clone of a global entry
 * takes over the store's products of its source once PIM's delayed announcement arrives, and gives them back when the
 * clone is deleted.
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

    /**
     * The delayed announcement of a new clone (R17): PIM sends it once the app instances have had time to load the
     * clone into their copies, yet the copy of this instance is refreshed first, synchronously, so the clone is looked
     * up in a copy that knows it. A refresh failure propagates and the event is redelivered. Nothing moves when the
     * refreshed copy has no such clone of the event's owner (deleted meanwhile, or not loaded by PIM yet: a WARN asks
     * for a manual resend) or when the clone's source differs from the event's.
     */
    void onCloneAnnounced(PIMEntryAddedEvent event, PimCatalog catalog) {
        String ownerStoreId = event.ownerStoreId();
        String sourcePimId = event.clonedFromPimId();
        if (ownerStoreId == null || isBlank(sourcePimId)) {
            return;
        }
        catalog.refresh();
        PimView storeView = catalog.withStoreData(ownerStoreId);
        Optional<PimEntry> clone = storeView.findByPimId(event.pimId())
                .filter(entry -> ownerStoreId.equals(entry.ownerStoreId()));
        if (clone.isEmpty()) {
            // deleted meanwhile, or PIM's index had not loaded the clone when this instance refreshed
            log.warn("PIM clone {} of {} in store {}: not in the refreshed PIM index, nothing moved; if the clone "
                            + "exists, resend pim-entry-added manually after the next PIM index rebuild, see runbook",
                    event.pimId(), sourcePimId, ownerStoreId);
            return;
        }
        if (!sourcePimId.equals(clone.get().clonedFromPimId())) {
            // the event must not move the products of a source the entry was not cloned from
            log.warn("PIM clone {} in store {}: the event names source {} but the entry was cloned from {}, nothing moved",
                    event.pimId(), ownerStoreId, sourcePimId, clone.get().clonedFromPimId());
            return;
        }
        List<String> ownerCategoryIds = categoryIdsOf(ownerStoreId);
        Set<String> categoryIds = Set.copyOf(ownerCategoryIds);
        List<Product> onSource = productRepository.findAllByPimId(sourcePimId).stream()
                .filter(projection -> categoryIds.contains(projection.getCategoryId()))
                .map(projection -> productRepository.findByProductId(projection.getCategoryId(), projection.getProductId()))
                .filter(Objects::nonNull)
                .filter(product -> sourcePimId.equals(product.getPimId()))
                .toList();
        onSource.forEach(product -> repin(product, sourcePimId, event.pimId(), event.pimId(),
                "moved from the source to the store's clone"));
        // the clone's codes in the refreshed copy, not the ones the event carries: the store may have changed them
        // during the delay, and those code-change events were already handled as for any store entry
        Codes current = Codes.of(clone.get().gtins(), clone.get().mpns());
        int pinned = attachMatchingProducts(event.pimId(), ownerStoreId, current, storeView, ownerCategoryIds);
        log.info("PIM clone {} of {} in store {} with EANs {} and MPNs {}: {} products moved from the source, {} pinned "
                        + "or moved here by code", event.pimId(), sourcePimId, ownerStoreId, current.eans(),
                current.mfnCodes(), onSource.size(), pinned);
    }

    /**
     * A product of a deleted clone goes back to the clone's source while the source is still global in the copy
     * (R18), also when only a sibling code pinned it; otherwise, and for any other entry, the code rule applies. The
     * copy is not refreshed yet when this runs, so it still holds the deleted entry and its source.
     */
    void onEntryDeleted(String pimId, PimCatalog catalog) {
        List<Product> detached = productRepository.detachPimFromProducts(pimId);
        if (detached.isEmpty()) {
            return;
        }
        PimView globalView = catalog.withGlobalData();
        CloneSources cloneSources = new CloneSources(pimId, catalog, globalView);
        detached.forEach(product -> cloneSources.sourceFor(product).ifPresentOrElse(
                sourcePimId -> repin(product, pimId, sourcePimId, pimId, "clone deleted, back to its source"),
                () -> pinToGlobalEntry(product, pimId, globalView).ifPresentOrElse(
                        // detachPimFromProducts already cleared the pin: the deleted entry is the old one
                        globalPimId -> repin(product, pimId, globalPimId, pimId, "entry deleted, pinned to a global entry"),
                        () -> log.info("PIM entry {} deleted: product {}/{} unpinned", pimId, product.getCategoryId(),
                                product.getProductId()))));
    }

    // the source of the deleted entry for the store owning a product's category; the owners are looked up once per
    // event and only when the deleted entry is not a global one (a global entry has no source)
    private final class CloneSources {

        private final String deletedPimId;
        private final PimCatalog catalog;
        private final PimView globalView;
        private final boolean global;
        private Map<String, String> storeOfCategory;

        CloneSources(String deletedPimId, PimCatalog catalog, PimView globalView) {
            this.deletedPimId = deletedPimId;
            this.catalog = catalog;
            this.globalView = globalView;
            this.global = globalView.findByPimId(deletedPimId).isPresent();
        }

        Optional<String> sourceFor(Product product) {
            if (global) {
                return Optional.empty();
            }
            String ownerStoreId = storeOfCategory().get(product.getCategoryId());
            if (ownerStoreId == null) {
                return Optional.empty();
            }
            return catalog.withStoreData(ownerStoreId).findByPimId(deletedPimId)
                    .filter(entry -> ownerStoreId.equals(entry.ownerStoreId()))
                    .map(PimEntry::clonedFromPimId)
                    .filter(source -> !isBlank(source) && !source.equals(deletedPimId))
                    .filter(source -> globalView.findByPimId(source).isPresent());
        }

        private Map<String, String> storeOfCategory() {
            if (storeOfCategory == null) {
                storeOfCategory = new HashMap<>();
                productCatalogRepository.findAllOfEveryStore().forEach(productCatalog ->
                        productCatalog.getCategories().forEach(category ->
                                storeOfCategory.putIfAbsent(category.getCategoryId(), productCatalog.getStoreId())));
            }
            return storeOfCategory;
        }
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
