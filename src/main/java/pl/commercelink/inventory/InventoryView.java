package pl.commercelink.inventory;

import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class InventoryView {

    private final Collection<MatchedInventory> globalInventory;
    private final Collection<MatchedInventory> ownInventory;
    private final InventoryFilterChain filterChain;

    InventoryView(Collection<MatchedInventory> globalInventory) {
        this(globalInventory, List.of());
    }

    InventoryView(Collection<MatchedInventory> globalInventory, InventoryFilter... filters) {
        this(globalInventory, List.of(), filters);
    }

    InventoryView(Collection<MatchedInventory> globalInventory, Collection<MatchedInventory> ownInventory, InventoryFilter... filters) {
        this.globalInventory = globalInventory;
        this.ownInventory = ownInventory;
        this.filterChain = new InventoryFilterChain(Arrays.asList(filters));
    }

    public MatchedInventory findByEan(String ean) {
        return findByInventoryKey(InventoryKey.fromEan(ean));
    }

    public MatchedInventory findByProductCode(String productCode) {
        return findByInventoryKey(InventoryKey.fromMfn(productCode));
    }

    public MatchedInventory findByProduct(Product product) {
        return findByInventoryKey(InventoryKey.fromProduct(product));
    }

    public MatchedInventory findByInventoryKey(InventoryKey lookupKey) {
        List<MatchedInventory> candidates = globalInventory.stream()
                .filter(i -> i.matches(lookupKey))
                .toList();

        MatchedInventory matchedInventory;
        if (candidates.size() == 1) {
            matchedInventory = candidates.getFirst();
        } else {
            List<MatchedInventory> closeMatches = candidates.stream()
                    .filter(i -> i.matchedByMfn(lookupKey))
                    .toList();

            matchedInventory = selectBestMatch(lookupKey, closeMatches, candidates);
        }

        return filterChain.apply(matchedInventory);
    }

    // prioritize lookup by mfn as it's more reliable
    private MatchedInventory selectBestMatch(InventoryKey lookupKey, List<MatchedInventory> matchesByMfn, List<MatchedInventory> allMatches) {
        MatchedInventory bestMatch = selectBestMatch(matchesByMfn);
        if (bestMatch != null) {
            return bestMatch;
        }

        bestMatch = selectBestMatch(allMatches);
        if (bestMatch != null) {
            return bestMatch;
        }

        return MatchedInventory.empty(lookupKey);
    }

    private MatchedInventory selectBestMatch(List<MatchedInventory> candidates) {
        MatchedInventory bestMatch = null;

        int bestMatchSize = 0;
        for (MatchedInventory candidate : candidates) {
            if (candidate.size() > bestMatchSize) {
                bestMatchSize = candidate.size();
                bestMatch = candidate;
            }
        }

        return bestMatch;
    }

    public Collection<MatchedInventory> findAllWithPimId() {
        return new ListingInventory(globalInventory, ownInventory).stream()
                .filter(i -> i.getInventoryKey().getId() != null)
                .map(filterChain::apply)
                .collect(Collectors.toList());
    }

    public Collection<MatchedInventory> findAllByProductCategory(ProductCategory productCategory) {
        return new ListingInventory(globalInventory, ownInventory).stream()
                .filter(i -> i.getTaxonomy().category() == productCategory)
                .map(filterChain::apply)
                .collect(Collectors.toList());
    }

}
