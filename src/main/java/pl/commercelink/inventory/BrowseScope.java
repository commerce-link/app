package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The global index seen by one store: only its enabled global suppliers, plus its own and manual feeds. An own offer
 * of a product the global index already lists is added to that product instead of listing it twice.
 */
final class BrowseScope {

    private final BrowseIndex global;
    private final Predicate<String> enabledGlobal;
    private final Map<MatchedInventory, List<InventoryItem>> ownOffersOfGlobal;
    private final BrowseIndex ownOnly;
    private volatile BrowseSummary summary;

    private BrowseScope(BrowseIndex global, Predicate<String> enabledGlobal,
                        Map<MatchedInventory, List<InventoryItem>> ownOffersOfGlobal, BrowseIndex ownOnly) {
        this.global = global;
        this.enabledGlobal = enabledGlobal;
        this.ownOffersOfGlobal = ownOffersOfGlobal;
        this.ownOnly = ownOnly;
    }

    static BrowseScope everyGlobalSupplier(BrowseIndex global) {
        return new BrowseScope(global, supplier -> true, Map.of(), BrowseIndex.build(global.version(), List.of()));
    }

    static BrowseScope of(BrowseIndex global, InventoryIndex globalGroups, Predicate<String> enabledGlobal,
                          Collection<MatchedInventory> own) {
        Map<MatchedInventory, List<InventoryItem>> extras = new IdentityHashMap<>();
        List<MatchedInventory> ownOnly = new ArrayList<>();
        for (MatchedInventory ownGroup : own) {
            // The browse index may lag one reload behind the live groups; a group it does not know is listed as own.
            Optional<MatchedInventory> listed = globalGroups.findMatching(ownGroup.getInventoryKey()).stream()
                    .filter(candidate -> global.entryOf(candidate).isPresent())
                    .findFirst();
            if (listed.isPresent()) {
                extras.computeIfAbsent(listed.get(), group -> new ArrayList<>()).addAll(ownGroup.getInventoryItems());
            } else {
                ownOnly.add(ownGroup);
            }
        }
        return new BrowseScope(global, enabledGlobal, extras, BrowseIndex.build(global.version(), ownOnly));
    }

    Stream<BrowseEntry> entries(Set<String> categoryIds) {
        if (categoryIds == null) {
            return Stream.concat(global.all().stream(), ownOnly.all().stream());
        }
        return Stream.concat(global.inCategories(categoryIds).stream(), ownOnly.inCategories(categoryIds).stream());
    }

    List<InventoryItem> offersOf(BrowseEntry entry) {
        MatchedInventory group = entry.group();
        if (ownOnly.entryOf(group).isPresent()) {
            return group.getInventoryItems();
        }
        List<InventoryItem> offers = new ArrayList<>();
        for (InventoryItem item : group.getInventoryItems()) {
            if (enabledGlobal.test(item.supplier())) {
                offers.add(item);
            }
        }
        offers.addAll(ownOffersOfGlobal.getOrDefault(group, List.of()));
        return offers;
    }

    BrowseSummary summary() {
        BrowseSummary current = summary;
        if (current == null) {
            current = count();
            summary = current;
        }
        return current;
    }

    private BrowseSummary count() {
        Map<String, Integer> byCategory = new HashMap<>();
        Map<String, Integer> bySupplier = new HashMap<>();
        int total = 0;
        for (BrowseEntry entry : (Iterable<BrowseEntry>) entries(null)::iterator) {
            List<InventoryItem> offers = offersOf(entry);
            if (offers.isEmpty()) {
                continue;
            }
            total++;
            byCategory.merge(entry.categoryId(), 1, Integer::sum);
            Set<String> suppliers = new HashSet<>();
            offers.forEach(offer -> suppliers.add(offer.supplier()));
            suppliers.forEach(supplier -> bySupplier.merge(supplier, 1, Integer::sum));
        }
        return new BrowseSummary(Map.copyOf(byCategory), Map.copyOf(bySupplier), total);
    }
}
