package pl.commercelink.web.orders;

import pl.commercelink.orders.filters.model.OrderFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything the orders list page prints, already resolved (spec §8.1). */
public record OrdersPageModel(
        OrderListQuery query,
        List<Tile> tiles,
        List<StatusOption> openStatuses,
        String statusSummary,
        List<FilterOption> filterOptions,
        Optional<OrderFilter> activeFilter,
        List<Chip> chips,
        String resultsLine,
        Map<OrderListQuery.Sort, SortHeader> sortHeaders,
        List<OrderRow> rows,
        Pagination pagination,
        EmptyState emptyState,
        PickupAction pickup) {

    /**
     * "Zamów odbiór" in the page header: the pickup page with the way back to this list, and how many packages wait for
     * a courier (null when none waits or the number could not be read). Null on the results fragment, which does not
     * carry the header.
     */
    public record PickupAction(String href, Integer waiting) {
    }

    /**
     * A figure above the list: label, number of the whole store's open orders, one short hint (the Asortyment cl-stat,
     * spec §17). A click narrows the list to those orders (href); active marks the tile narrowing it now, whose href
     * lets it go.
     */
    public record Tile(String label, long count, String hint, String href, boolean active) {
    }

    /** One row of the Status menu: the status, its count within the custom filter and the search, and whether it is ticked. */
    public record StatusOption(String status, String label, long count, boolean selected) {
    }

    /**
     * A saved filter in the Filter menu; href also ticks the filter's own Status condition, if it has one, and
     * {@code isDefault} marks the one the user's list opens with.
     */
    public record FilterOption(String id, String label, boolean shared, boolean selected, boolean isDefault, String href) {
    }

    public record Chip(String label, String clearHref, String clearLabel) {
    }

    public record SortHeader(String href, String ariaSort) {
    }

    public record EmptyState(String text, String actionLabel, String actionHref) {
    }
}
