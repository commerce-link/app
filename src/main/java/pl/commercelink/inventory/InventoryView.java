package pl.commercelink.inventory;

import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class InventoryView {

    private final MatchedInventorySource source;
    private final InventoryFilterChain filterChain;

    InventoryView(MatchedInventorySource source) {
        this.source = source;
        this.filterChain = new InventoryFilterChain(new LinkedList<>());
    }

    InventoryView(MatchedInventorySource source, InventoryFilter... filters) {
        this.source = source;
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
        List<MatchedInventory> candidates = source.candidatesFor(lookupKey).stream()
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
        return source.all().stream()
                .filter(i -> i.getInventoryKey().getId() != null)
                .collect(Collectors.toList());
    }

    public Collection<MatchedInventory> findAllByProductCategory(ProductCategory productCategory) {
        return source.all().stream()
                .filter(i -> i.getTaxonomy().category() == productCategory)
                .map(filterChain::apply)
                .collect(Collectors.toList());
    }

}
