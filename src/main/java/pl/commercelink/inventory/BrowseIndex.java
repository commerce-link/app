package pl.commercelink.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable list of browsable products grouped by PIM category id, built for one version of the global inventory. */
public final class BrowseIndex {

    /** Node of products whose taxonomy has a category text but no PIM category id ("Bez kategorii PIM"). */
    public static final String UNASSIGNED = "-";

    private final long version;
    private final List<BrowseEntry> all;
    private final Map<String, List<BrowseEntry>> byCategory;
    private final Map<MatchedInventory, BrowseEntry> byGroup;

    private BrowseIndex(long version, List<BrowseEntry> all, Map<String, List<BrowseEntry>> byCategory,
                        Map<MatchedInventory, BrowseEntry> byGroup) {
        this.version = version;
        this.all = all;
        this.byCategory = byCategory;
        this.byGroup = byGroup;
    }

    public static BrowseIndex build(long version, Collection<MatchedInventory> groups) {
        List<BrowseEntry> all = new ArrayList<>(groups.size());
        Map<String, List<BrowseEntry>> byCategory = new HashMap<>();
        Map<MatchedInventory, BrowseEntry> byGroup = new IdentityHashMap<>(groups.size());
        for (MatchedInventory group : groups) {
            BrowseEntry entry = new BrowseEntry(group, group.getTaxonomy());
            all.add(entry);
            byCategory.computeIfAbsent(entry.categoryId(), id -> new ArrayList<>()).add(entry);
            byGroup.put(group, entry);
        }
        return new BrowseIndex(version, Collections.unmodifiableList(all), byCategory,
                Collections.unmodifiableMap(byGroup));
    }

    public long version() {
        return version;
    }

    public int size() {
        return all.size();
    }

    public List<BrowseEntry> all() {
        return all;
    }

    public List<BrowseEntry> inCategories(Collection<String> categoryIds) {
        List<BrowseEntry> entries = new ArrayList<>();
        for (String id : categoryIds) {
            entries.addAll(byCategory.getOrDefault(id, List.of()));
        }
        return entries;
    }

    public Optional<BrowseEntry> entryOf(MatchedInventory group) {
        return Optional.ofNullable(byGroup.get(group));
    }
}
