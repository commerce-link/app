package pl.commercelink.inventory;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.SignalCategoryResolver;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * categoryKey -> inventory keys, rebuilt every Inventory.load cycle. Lets a catalog subscribed to a
 * category pick up inventory that newer feeds bring under that category without re-subscribing
 * (the "resubscription" problem, pcp:415). The effective category is the taxonomy categoryKey, or,
 * when that is still Other, whatever the deterministic SignalCategoryResolver derives from its signals.
 */
@Component
public class CategoryReverseIndex {

    private static final String OTHER = ProductCategory.Other.name();

    private final SignalCategoryResolver signalCategoryResolver;

    private volatile Map<String, Set<InventoryKey>> keysByCategory = Map.of();

    public CategoryReverseIndex(SignalCategoryResolver signalCategoryResolver) {
        this.signalCategoryResolver = signalCategoryResolver;
    }

    public void rebuild(Collection<MatchedInventory> items) {
        Map<String, Set<InventoryKey>> rebuilt = new ConcurrentHashMap<>();
        for (MatchedInventory item : items) {
            String categoryKey = effectiveCategoryKey(item.getTaxonomy());
            rebuilt.computeIfAbsent(categoryKey, key -> ConcurrentHashMap.newKeySet()).add(item.getInventoryKey());
        }
        this.keysByCategory = rebuilt;
    }

    public Set<InventoryKey> keysFor(String categoryKey) {
        return Set.copyOf(keysByCategory.getOrDefault(categoryKey, Set.of()));
    }

    private String effectiveCategoryKey(Taxonomy taxonomy) {
        String categoryKey = taxonomy.categoryKey();
        if (categoryKey != null && !OTHER.equals(categoryKey)) {
            return categoryKey;
        }
        return signalCategoryResolver.resolve(taxonomy.signals()).orElse(categoryKey);
    }
}
