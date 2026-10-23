package pl.commercelink.inventory;

import pl.commercelink.taxonomy.Taxonomy;

import java.util.Collection;

/**
 * One product of the browse index: the matched group as the inventory already holds it, its taxonomy, its place in the
 * Polish name order and its codes. The group and taxonomy are not copied, so the index costs a few references per
 * product (spec §5.3, memory budget).
 */
public final class BrowseEntry {

    private static final String[] NO_CODES = new String[0];

    private final MatchedInventory group;
    private final Taxonomy taxonomy;
    private final long nameRank;
    // The key normalizes its codes on every read; a phrase scans every product, so they are read once here.
    private final String[] codes;

    BrowseEntry(MatchedInventory group, Taxonomy taxonomy, long nameRank) {
        this.group = group;
        this.taxonomy = taxonomy;
        this.nameRank = nameRank;
        this.codes = codesOf(group.getInventoryKey());
    }

    public MatchedInventory group() {
        return group;
    }

    public Taxonomy taxonomy() {
        return taxonomy;
    }

    /** Position in the Polish collation order of names; comparing two ranks orders by name without a collator. */
    long nameRank() {
        return nameRank;
    }

    public String categoryId() {
        String id = taxonomy.categoryId();
        return id == null || id.isBlank() ? BrowseIndex.UNASSIGNED : id.strip();
    }

    public String name() {
        return taxonomy.name();
    }

    public String brand() {
        return taxonomy.brand();
    }

    /** Case-insensitive "contains" without lower-casing a copy of each field: 600 thousand products per query. */
    public boolean matchesText(String needle) {
        if (contains(taxonomy.name(), needle) || contains(taxonomy.brand(), needle)) {
            return true;
        }
        for (String code : codes) {
            if (contains(code, needle)) {
                return true;
            }
        }
        return false;
    }

    private static String[] codesOf(InventoryKey key) {
        Collection<String> eans = key.getProductEans();
        Collection<String> mfns = key.getProductCodes();
        if (eans.isEmpty() && mfns.isEmpty()) {
            return NO_CODES;
        }
        String[] codes = new String[eans.size() + mfns.size()];
        int i = 0;
        for (String ean : eans) {
            codes[i++] = ean;
        }
        for (String mfn : mfns) {
            codes[i++] = mfn;
        }
        return codes;
    }

    static boolean contains(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        int last = haystack.length() - needle.length();
        for (int start = 0; start <= last; start++) {
            if (haystack.regionMatches(true, start, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }
}
