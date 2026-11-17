package pl.commercelink.inventory;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class GlobalMatchedInventory {

    private volatile Collection<MatchedInventory> matched = new LinkedList<>();
    // the index of the current groups, built on first use; replaced, never cleared, on every reload, so generations
    // taken before a reload keep sharing the old one while they live and nothing here pins it afterwards
    private volatile LazyIndex index = LazyIndex.of(matched);
    private volatile long version;
    private volatile VariantCodes variantCodes = VariantCodes.none();

    public synchronized void replace(Collection<MatchedInventory> matched) {
        replace(matched, VariantCodes.none());
    }

    /** @param variantCodes the variant codes of the global pool; a store inherits them to link its own groups */
    public synchronized void replace(Collection<MatchedInventory> matched, VariantCodes variantCodes) {
        this.matched = matched;
        this.variantCodes = variantCodes;
        this.index = LazyIndex.of(matched);
        this.version++;
    }

    public long version() {
        return version;
    }

    /**
     * The groups, variant codes and version of one reload, read together so they always belong to each other. Taking a
     * generation does not build the index: the store build takes one before grouping its own offers and must not hold
     * the global index through that peak. The generation's index is the one of its reload, shared with every other
     * user of that reload and built outside this object's lock.
     */
    synchronized Generation generation() {
        return new Generation(version, matched, index, variantCodes);
    }

    static final class Generation {

        private final long version;
        private final Collection<MatchedInventory> all;
        private final VariantCodes variantCodes;
        private final LazyIndex index;

        Generation(long version, Collection<MatchedInventory> all, InventoryIndex index, VariantCodes variantCodes) {
            this(version, all, LazyIndex.built(index), variantCodes);
        }

        /** @param indexSource called on {@link #index()} until it first returns an index, never after */
        Generation(long version, Collection<MatchedInventory> all, Supplier<InventoryIndex> indexSource, VariantCodes variantCodes) {
            this(version, all, new LazyIndex(indexSource), variantCodes);
        }

        private Generation(long version, Collection<MatchedInventory> all, LazyIndex index, VariantCodes variantCodes) {
            this.version = version;
            this.all = all;
            this.index = index;
            this.variantCodes = variantCodes;
        }

        long version() {
            return version;
        }

        Collection<MatchedInventory> all() {
            return all;
        }

        VariantCodes variantCodes() {
            return variantCodes;
        }

        /** The index of this generation's groups, built on first use and shared with the other users of its reload. */
        InventoryIndex index() {
            return index.get();
        }

        /** The offers of every group, flattened on each call. */
        List<InventoryItem> allItems() {
            return itemsOf(all);
        }
    }

    /** An index built once, on first use, under its own lock. */
    private static final class LazyIndex {

        private Supplier<InventoryIndex> source;
        private volatile InventoryIndex index;

        private LazyIndex(Supplier<InventoryIndex> source) {
            this.source = Objects.requireNonNull(source, "index source");
        }

        private LazyIndex(InventoryIndex built) {
            this.index = Objects.requireNonNull(built, "index");
        }

        static LazyIndex of(Collection<MatchedInventory> groups) {
            return new LazyIndex(() -> InventoryIndex.of(groups));
        }

        static LazyIndex built(InventoryIndex index) {
            return new LazyIndex(index);
        }

        InventoryIndex get() {
            InventoryIndex current = index;
            if (current == null) {
                synchronized (this) {
                    current = index;
                    if (current == null) {
                        current = source.get();
                        if (current == null) {
                            // source stays, so every call fails the same way instead of with an NPE later
                            throw new IllegalStateException("The index source returned no index");
                        }
                        index = current;
                        source = null;
                    }
                }
            }
            return current;
        }
    }

    public Collection<MatchedInventory> all() {
        return matched;
    }

    public InventoryIndex index() {
        return index.get();
    }

    public List<InventoryItem> allItems() {
        return itemsOf(matched);
    }

    private static List<InventoryItem> itemsOf(Collection<MatchedInventory> matched) {
        return matched.stream()
                .flatMap(i -> i.getInventoryItems().stream())
                .collect(Collectors.toList());
    }

    public int size() {
        return matched.size();
    }
}
