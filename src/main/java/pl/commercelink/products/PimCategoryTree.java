package pl.commercelink.products;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.text.Collator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Polish PIM category tree, built once per ten minutes. {@link PimCategoryOptions} rebuilds the tree on every call,
 * which is fine for a settings form but not for a list that asks for paths of fifty rows.
 */
@Component
@RequiredArgsConstructor
public class PimCategoryTree {

    private static final String LANG = "pl";
    private static final Collator POLISH = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
    private static final Comparator<PimCategory> BY_NAME = Comparator.comparing(PimCategory::name, POLISH);

    private final PimCatalog pimCatalog;
    private final Cache<String, Snapshot> cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public Optional<PimCategory> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(snapshot().byId().get(id));
    }

    public List<PimCategory> topLevels() {
        return snapshot().topLevels();
    }

    public List<PimCategory> childrenOf(String id) {
        return snapshot().children().getOrDefault(id, List.of());
    }

    public List<PimCategory> siblingsOf(String id) {
        return find(id)
                .map(category -> category.topLevel() ? topLevels() : childrenOf(category.parentId()))
                .orElse(List.of());
    }

    public Optional<String> parentIdOf(String id) {
        return find(id).map(PimCategory::parentId);
    }

    public List<String> pathNames(String id) {
        Map<String, PimCategory> byId = snapshot().byId();
        LinkedList<String> names = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        PimCategory current = id == null ? null : byId.get(id);
        // A malformed tree with a cycle must not hang a page render.
        while (current != null && seen.add(current.id())) {
            names.addFirst(current.name());
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return List.copyOf(names);
    }

    public Set<String> selfAndDescendants(String id) {
        if (find(id).isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        LinkedList<String> pending = new LinkedList<>(List.of(id));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (ids.add(next)) {
                childrenOf(next).forEach(child -> pending.add(child.id()));
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    private Snapshot snapshot() {
        return cache.get("tree", key -> build(pimCatalog.allCategories()));
    }

    private static Snapshot build(List<PimCategory> all) {
        Map<String, PimCategory> byId = new HashMap<>();
        for (PimCategory category : all) {
            if (LANG.equals(category.lang())) {
                byId.put(category.id(), category);
            }
        }
        Map<String, List<PimCategory>> children = new HashMap<>();
        List<PimCategory> topLevels = new ArrayList<>();
        for (PimCategory category : byId.values()) {
            if (category.topLevel()) {
                topLevels.add(category);
            } else {
                children.computeIfAbsent(category.parentId(), parent -> new ArrayList<>()).add(category);
            }
        }
        topLevels.sort(BY_NAME);
        Map<String, List<PimCategory>> sortedChildren = new HashMap<>();
        children.forEach((parent, list) -> {
            list.sort(BY_NAME);
            sortedChildren.put(parent, List.copyOf(list));
        });
        return new Snapshot(Map.copyOf(byId), Map.copyOf(sortedChildren), List.copyOf(topLevels));
    }

    private record Snapshot(Map<String, PimCategory> byId, Map<String, List<PimCategory>> children,
                            List<PimCategory> topLevels) {
    }
}
