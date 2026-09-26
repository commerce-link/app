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
        EmptyState emptyState) {

    /** A read-only figure above the list: label, number, one short hint (the Asortyment cl-stat, spec §17). */
    public record Tile(String label, long count, String hint) {
    }

    /** One row of the Status menu: the status, its count within the custom filter and the search, and whether it is ticked. */
    public record StatusOption(String status, String label, long count, boolean selected) {
    }

    /** A saved filter in the Filter menu; href also ticks the filter's own Status condition, if it has one. */
    public record FilterOption(String id, String label, boolean shared, boolean selected, String href) {
    }

    public record Chip(String label, String clearHref, String clearLabel) {
    }

    public record SortHeader(String href, String ariaSort) {
    }

    public record EmptyState(String text, String actionLabel, String actionHref) {
    }
}
