package pl.commercelink.inventory;

import java.util.Set;

/** What one browse request asks for. {@code categoryIds == null} means every category. */
public record BrowseCriteria(Set<String> categoryIds, Set<String> suppliers, String text, Sort sort, boolean descending,
                             int offset, int limit) {

    public static final int MAX_TEXT_MATCHES = 2_000;

    public enum Sort { NAME, COST, QTY }

    public static BrowseCriteria all() {
        return new BrowseCriteria(null, Set.of(), null, Sort.NAME, false, 0, 50);
    }

    public BrowseCriteria inCategories(Set<String> ids) {
        return new BrowseCriteria(ids, suppliers, text, sort, descending, offset, limit);
    }

    public BrowseCriteria fromSuppliers(Set<String> names) {
        return new BrowseCriteria(categoryIds, Set.copyOf(names), text, sort, descending, offset, limit);
    }

    public BrowseCriteria withText(String newText) {
        return new BrowseCriteria(categoryIds, suppliers, newText, sort, descending, offset, limit);
    }

    public BrowseCriteria sortedBy(Sort newSort, boolean newDescending) {
        return new BrowseCriteria(categoryIds, suppliers, text, newSort, newDescending, offset, limit);
    }

    public BrowseCriteria page(int newOffset, int newLimit) {
        return new BrowseCriteria(categoryIds, suppliers, text, sort, descending, Math.max(0, newOffset), Math.max(1, newLimit));
    }
}
