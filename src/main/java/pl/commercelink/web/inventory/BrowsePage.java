package pl.commercelink.web.inventory;

import pl.commercelink.web.orders.Pagination;

import java.util.List;
import java.util.Map;

/** Everything the browse mode of the inventory page renders; labels that need translation travel as message keys. */
public record BrowsePage(BrowseQuery query, boolean admin, boolean noSuppliers, boolean textTooShort,
                         String title, List<Crumb> crumbs, List<NavItem> subnav, boolean subnavSiblings,
                         List<Tile> tiles, List<MenuOption> supplierOptions, List<Chip> chips, String clearHref,
                         List<RowView> rows, int total, boolean truncated, Pagination pagination,
                         Map<String, SortHeader> sortHeaders, Status status) {

    public static final String PAGE_PATH = BrowseQuery.PATH;
    public static final String FRAGMENT_PATH = BrowseQuery.FRAGMENT_PATH;

    /**
     * READY is the list or the tiles. BUILDING: the index of the inventory is not in place yet (the first seconds after a
     * start), PIM_UNAVAILABLE: the category tree could not be read, UNKNOWN_CATEGORY: {@code cat} names no category.
     */
    public enum Status { READY, BUILDING, PIM_UNAVAILABLE, UNKNOWN_CATEGORY }

    /** A page that shows only its state: nothing was counted or listed for it. */
    public static BrowsePage of(Status status, BrowseQuery query, boolean admin) {
        return new BrowsePage(query, admin, false, false, null, List.of(), List.of(), false, List.of(),
                List.of(), List.of(), query.cleared().href(), List.of(), 0, false,
                Pagination.of(1, 0, BrowseQuery.PAGE_SIZE, page -> query.withPage(page).href()), Map.of(), status);
    }

    public boolean ready() {
        return status == Status.READY;
    }

    public boolean building() {
        return status == Status.BUILDING;
    }

    public boolean pimUnavailable() {
        return status == Status.PIM_UNAVAILABLE;
    }

    public boolean unknownCategory() {
        return status == Status.UNKNOWN_CATEGORY;
    }

    public String startHref() {
        return BrowseQuery.start().href();
    }

    public boolean isStart() {
        return query.isStart();
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

    public record NavItem(String label, int count, String href, boolean current) {
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

        /**
         * The dialog and "Uzupełnij dane" find the product by its EAN; a row without one (an own or manual offer listed
         * by its manufacturer code only) has neither the checkbox nor the add action.
         */
        public boolean addable() {
            return ean != null && !ean.isBlank();
        }
    }
}
