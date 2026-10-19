package pl.commercelink.web.inventory;

import pl.commercelink.products.PimCategoryTree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Products per PIM category including everything below it, from the counts of the leaves the products sit in. */
final class CategoryCounts {

    private CategoryCounts() {
    }

    static Map<String, Integer> rollUp(Map<String, Integer> leafCounts, PimCategoryTree tree) {
        Map<String, Integer> counts = new HashMap<>();
        leafCounts.forEach((id, count) -> {
            Set<String> seen = new HashSet<>();
            String current = id;
            while (current != null && seen.add(current)) {
                counts.merge(current, count, Integer::sum);
                Optional<String> parent = tree.parentIdOf(current);
                current = parent.orElse(null);
            }
        });
        return counts;
    }
}
