package pl.commercelink.inventory;

import pl.commercelink.taxonomy.Taxonomy;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable list of browsable products grouped by PIM category id, built for one version of the global inventory. */
public final class BrowseIndex {

    /** Node of products whose taxonomy has a category text but no PIM category id ("Bez kategorii PIM"). */
    public static final String UNASSIGNED = "-";

    private static final Collator POLISH = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
    // A product of the index at name position p has rank (2p + 1) << SHIFT; a product ranked alongside it that sorts
    // just before position p gets (2p) << SHIFT plus its own position, so both kinds compare as plain numbers.
    private static final int SHIFT = 31;

    private final long version;
    private final List<BrowseEntry> all;
    private final Map<String, List<BrowseEntry>> byCategory;
    private final Map<MatchedInventory, BrowseEntry> byGroup;
    private final BrowseEntry[] byName;
    private volatile InventoryIndex groups;

    private BrowseIndex(long version, List<BrowseEntry> all, Map<String, List<BrowseEntry>> byCategory,
                        Map<MatchedInventory, BrowseEntry> byGroup, BrowseEntry[] byName, InventoryIndex groups) {
        this.version = version;
        this.all = all;
        this.byCategory = byCategory;
        this.byGroup = byGroup;
        this.byName = byName;
        this.groups = groups;
    }

    public static BrowseIndex build(long version, Collection<MatchedInventory> groups) {
        return build(version, groups, null);
    }

    /** {@code groupsIndex} is the inventory's own index of the same groups, reused to match a store's own feed. */
    static BrowseIndex build(long version, Collection<MatchedInventory> groups, InventoryIndex groupsIndex) {
        List<MatchedInventory> list = List.copyOf(groups);
        Taxonomy[] taxonomies = taxonomiesOf(list);
        int[] order = nameOrder(taxonomies);
        long[] ranks = new long[list.size()];
        for (int position = 0; position < order.length; position++) {
            ranks[order[position]] = (2L * position + 1) << SHIFT;
        }
        return assemble(version, list, taxonomies, ranks, order, groupsIndex);
    }

    /**
     * Products that are not in this index (a store's own feed), ranked into this index's name order, so a list mixing
     * both sorts by name without comparing names again.
     */
    BrowseIndex alongside(Collection<MatchedInventory> groups) {
        List<MatchedInventory> list = List.copyOf(groups);
        Taxonomy[] taxonomies = taxonomiesOf(list);
        int[] order = nameOrder(taxonomies);
        Collator collator = (Collator) POLISH.clone();
        long[] ranks = new long[list.size()];
        for (int position = 0; position < order.length; position++) {
            int before = countNotAfter(taxonomies[order[position]].name(), collator);
            ranks[order[position]] = ((2L * before) << SHIFT) | position;
        }
        return assemble(version, list, taxonomies, ranks, order, null);
    }

    private static BrowseIndex assemble(long version, List<MatchedInventory> list, Taxonomy[] taxonomies, long[] ranks,
                                        int[] order, InventoryIndex groupsIndex) {
        List<BrowseEntry> all = new ArrayList<>(list.size());
        Map<String, List<BrowseEntry>> byCategory = new HashMap<>();
        Map<MatchedInventory, BrowseEntry> byGroup = new IdentityHashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            MatchedInventory group = list.get(i);
            BrowseEntry entry = new BrowseEntry(group, taxonomies[i], ranks[i]);
            all.add(entry);
            byCategory.computeIfAbsent(entry.categoryId(), id -> new ArrayList<>()).add(entry);
            byGroup.put(group, entry);
        }
        BrowseEntry[] byName = new BrowseEntry[order.length];
        for (int position = 0; position < order.length; position++) {
            byName[position] = all.get(order[position]);
        }
        return new BrowseIndex(version, Collections.unmodifiableList(all), byCategory,
                Collections.unmodifiableMap(byGroup), byName, groupsIndex);
    }

    private static Taxonomy[] taxonomiesOf(List<MatchedInventory> list) {
        Taxonomy[] taxonomies = new Taxonomy[list.size()];
        for (int i = 0; i < taxonomies.length; i++) {
            taxonomies[i] = list.get(i).getTaxonomy();
        }
        return taxonomies;
    }

    /** Positions of the products sorted by name (Polish collation, missing names last); ties keep the input order. */
    private static int[] nameOrder(Taxonomy[] taxonomies) {
        // Collation keys compare as bytes: sorting by them is far cheaper than calling the collator n log n times.
        Collator collator = (Collator) POLISH.clone();
        CollationKey[] keys = new CollationKey[taxonomies.length];
        Integer[] positions = new Integer[taxonomies.length];
        for (int i = 0; i < taxonomies.length; i++) {
            String name = taxonomies[i].name();
            keys[i] = name == null ? null : collator.getCollationKey(name);
            positions[i] = i;
        }
        Arrays.sort(positions, Comparator.comparing((Integer i) -> keys[i], Comparator.nullsLast(Comparator.naturalOrder())));
        return Arrays.stream(positions).mapToInt(Integer::intValue).toArray();
    }

    /** How many products of this index sort before or level with the name; a missing name sorts after all of them. */
    private int countNotAfter(String name, Collator collator) {
        if (name == null) {
            return byName.length;
        }
        int low = 0;
        int high = byName.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            String other = byName[middle].name();
            if (other != null && collator.compare(other, name) <= 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
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

    /**
     * The group of this index that a product with the key joins. Looked up among this index's own generation of groups:
     * the live inventory may already hold the next one, whose groups this index does not know.
     */
    Optional<MatchedInventory> findListed(InventoryKey key) {
        return groupsIndex().findMatching(key).stream().filter(byGroup::containsKey).findFirst();
    }

    private InventoryIndex groupsIndex() {
        InventoryIndex current = groups;
        if (current == null) {
            synchronized (this) {
                current = groups;
                if (current == null) {
                    current = InventoryIndex.of(all.stream().map(BrowseEntry::group).toList());
                    groups = current;
                }
            }
        }
        return current;
    }
}
