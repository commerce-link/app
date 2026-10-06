package pl.commercelink.web.inventory;

import pl.commercelink.web.orders.Pagination;

import java.util.List;
import java.util.Map;

/** Everything the browse mode of the inventory page renders; labels that need translation travel as message keys. */
public record BrowsePage(BrowseQuery query, boolean admin, boolean superAdmin, boolean noSuppliers, boolean textTooShort,
                         String title, List<Crumb> crumbs, List<NavItem> subnav, boolean subnavSiblings,
                         List<Tile> tiles, List<MenuOption> supplierOptions, List<Chip> chips, String clearHref,
                         List<RowView> rows, int total, boolean truncated, Pagination pagination,
                         Map<String, SortHeader> sortHeaders, String returnTo) {

    public static final String PAGE_PATH = BrowseQuery.PATH;
    public static final String FRAGMENT_PATH = BrowseQuery.FRAGMENT_PATH;

    public boolean isStart() {
        return query.isStart();
    }

    public boolean hasText() {
        return query.q2() != null && !textTooShort;
    }

    public String pagePath() {
        return PAGE_PATH;
    }

    public String fragmentPath() {
        return FRAGMENT_PATH;
    }

    /** {@code label == null} is the root ("Wszystkie kategorie") or the unassigned node; {@code href == null} is the current one. */
    public record Crumb(String label, String labelKey, String href) {
    }

    public record NavItem(String label, String labelKey, int count, String href, boolean current) {
    }

    public record Tile(String label, String labelKey, int count, String description, String href) {
    }

    public record MenuOption(String value, String label, int count, boolean selected) {
    }

    public record Chip(String labelKey, String value, String clearHref) {
    }

    public record SortHeader(String href, String ariaSort) {
    }

    public record RowView(String name, String brand, String ean, String mfn, String detailHref, CategoryLine category,
                          double cost, boolean deliveryKnown, String costSupplier, long qty, int suppliers,
                          long warehouseQty, String addHref) {

        public boolean inCatalog() {
            return category.inCatalog();
        }
    }
}
