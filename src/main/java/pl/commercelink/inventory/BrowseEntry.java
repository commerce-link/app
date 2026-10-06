package pl.commercelink.inventory;

import pl.commercelink.taxonomy.Taxonomy;

import java.util.Collection;

/**
 * One product of the browse index: the matched group as the inventory already holds it and its taxonomy. Nothing is
 * copied, so the index costs a few references per product (spec §5.3, memory budget).
 */
public record BrowseEntry(MatchedInventory group, Taxonomy taxonomy) {

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
        return contains(taxonomy.name(), needle)
                || contains(taxonomy.brand(), needle)
                || anyContains(group.getEans(), needle)
                || anyContains(group.getMfnCodes(), needle);
    }

    private static boolean anyContains(Collection<String> values, String needle) {
        for (String value : values) {
            if (contains(value, needle)) {
                return true;
            }
        }
        return false;
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
