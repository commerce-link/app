package pl.commercelink.products.information;

import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a store's clone of a global PIM entry and the source of a store's clone, from the store's view of the
 * current index copy. A clone (a store entry with {@code clonedFromPimId}) replaces its source for that store only, so
 * data keyed by pimId (prices, offers, discounts, marketplace exports) must read both ids. Read-only and null-safe:
 * a blank store or id gives an empty result, or the id itself where a String is returned.
 */
@Component
public class StorePimAliases {

    private final PimCatalog catalog;

    public StorePimAliases(PimCatalog catalog) {
        this.catalog = catalog;
    }

    /** The store's clone of the global entry {@code sourcePimId}. */
    public Optional<String> cloneOf(String storeId, String sourcePimId) {
        if (isBlank(storeId) || isBlank(sourcePimId)) {
            return Optional.empty();
        }
        return catalog.withStoreData(storeId).findCloneOf(sourcePimId).map(PimEntry::pimId);
    }

    /** The global entry the store's own entry {@code pimId} was cloned from; empty for any other entry. */
    public Optional<String> sourceOf(String storeId, String pimId) {
        if (isBlank(storeId) || isBlank(pimId)) {
            return Optional.empty();
        }
        return catalog.withStoreData(storeId).findByPimId(pimId)
                .filter(entry -> storeId.equals(entry.ownerStoreId()))
                .map(PimEntry::clonedFromPimId)
                .filter(source -> !isBlank(source));
    }

    /** {@code pimId} together with its source and its clone in the store, whichever exist. */
    public Set<String> aliasesOf(String storeId, String pimId) {
        if (isBlank(pimId)) {
            return Set.of();
        }
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(pimId);
        sourceOf(storeId, pimId).ifPresent(aliases::add);
        cloneOf(storeId, pimId).ifPresent(aliases::add);
        return Set.copyOf(aliases);
    }

    /** Every clone the store owns, mapped to the global entry it was cloned from. */
    public Map<String, String> clonesWithSources(String storeId) {
        if (isBlank(storeId)) {
            return Map.of();
        }
        Map<String, String> clones = new LinkedHashMap<>();
        for (PimEntry entry : catalog.withStoreData(storeId).findOwnClones()) {
            if (storeId.equals(entry.ownerStoreId()) && !isBlank(entry.clonedFromPimId())) {
                clones.put(entry.pimId(), entry.clonedFromPimId());
            }
        }
        return clones;
    }

    /**
     * Whether this instance's index copy resolves {@code pimId} at all, as a global entry or as an entry the store
     * sees. A copy older than an entry (a fresh clone) does not, and the store's data pinned to it cannot be read yet.
     */
    public boolean knows(String storeId, String pimId) {
        if (isBlank(pimId)) {
            return false;
        }
        if (catalog.withGlobalData().findByPimId(pimId).isPresent()) {
            return true;
        }
        return !isBlank(storeId) && catalog.withStoreData(storeId).findByPimId(pimId).isPresent();
    }

    /** The id an offer of the product is published under: a clone keeps its source's offer id. */
    public String offerIdOf(String storeId, String pimId) {
        return sourceOf(storeId, pimId).orElse(pimId);
    }

    /**
     * The pricelist row of the product {@code pimId} for the store: the row with exactly that id, else the row of the
     * store's clone of it, else the row of the global entry it was cloned from. A pricelist built before a product moved
     * onto its clone is keyed by the source, one built after it by the clone, and a cart or offer may hold either id.
     */
    public Optional<AvailabilityAndPrice> find(String storeId, Pricelist pricelist, String pimId) {
        if (pricelist == null || isBlank(pimId)) {
            return Optional.empty();
        }
        Optional<AvailabilityAndPrice> exact = pricelist.findByPimId(pimId);
        if (exact.isPresent()) {
            return exact;
        }
        return cloneOf(storeId, pimId).flatMap(pricelist::findByPimId)
                .or(() -> sourceOf(storeId, pimId).flatMap(pricelist::findByPimId));
    }

    /** The entry the store uses for {@code pimId}: the store's clone of that id, else the entry the store sees by id. */
    public Optional<PimEntry> effectiveEntry(String storeId, String pimId) {
        if (isBlank(storeId) || isBlank(pimId)) {
            return Optional.empty();
        }
        var storeView = catalog.withStoreData(storeId);
        return storeView.findCloneOf(pimId).or(() -> storeView.findByPimId(pimId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
