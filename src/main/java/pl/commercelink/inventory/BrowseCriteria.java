package pl.commercelink.inventory;

import java.util.Set;
import java.util.function.Predicate;

/** What one browse request asks for. {@code categoryIds == null} means every category. */
public record BrowseCriteria(Set<String> categoryIds, Set<String> suppliers, Stock stock, String text,
                             Predicate<BrowseRow> rowFilter, Sort sort, boolean descending, int offset, int limit) {

    public static final int MAX_TEXT_MATCHES = 2_000;

    public enum Stock { ALL, IN_STOCK, ON_ORDER }

    public enum Sort { NAME, COST, QTY }

    public static BrowseCriteria all() {
        return new BrowseCriteria(null, Set.of(), Stock.ALL, null, row -> true, Sort.NAME, false, 0, 50);
    }

    public BrowseCriteria inCategories(Set<String> ids) {
        return new BrowseCriteria(ids, suppliers, stock, text, rowFilter, sort, descending, offset, limit);
    }

    public BrowseCriteria fromSuppliers(Set<String> names) {
        return new BrowseCriteria(categoryIds, Set.copyOf(names), stock, text, rowFilter, sort, descending, offset, limit);
    }

    public BrowseCriteria withStock(Stock newStock) {
        return new BrowseCriteria(categoryIds, suppliers, newStock, text, rowFilter, sort, descending, offset, limit);
    }

    public BrowseCriteria withText(String newText) {
        return new BrowseCriteria(categoryIds, suppliers, stock, newText, rowFilter, sort, descending, offset, limit);
    }

    public BrowseCriteria withRowFilter(Predicate<BrowseRow> filter) {
        return new BrowseCriteria(categoryIds, suppliers, stock, text, filter, sort, descending, offset, limit);
    }

    public BrowseCriteria sortedBy(Sort newSort, boolean newDescending) {
        return new BrowseCriteria(categoryIds, suppliers, stock, text, rowFilter, newSort, newDescending, offset, limit);
    }

    public BrowseCriteria page(int newOffset, int newLimit) {
        return new BrowseCriteria(categoryIds, suppliers, stock, text, rowFilter, sort, descending,
                Math.max(0, newOffset), Math.max(1, newLimit));
    }
}
