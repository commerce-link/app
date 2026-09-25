package pl.commercelink.web.orders;

import pl.commercelink.orders.OrderAttention;
import pl.commercelink.orders.filters.model.OrderFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything the orders list page prints, already resolved (spec §8.1). */
public record OrdersPageModel(
        OrderListQuery query,
        List<Tile> tiles,
        List<Segment> openSegments,
        List<Segment> historySegments,
        List<FilterOption> filterOptions,
        Optional<OrderFilter> activeFilter,
        boolean activeFilterStarred,
        List<Chip> chips,
        String resultsLine,
        Map<OrderListQuery.Sort, SortHeader> sortHeaders,
        List<OrderRow> rows,
        Pagination pagination,
        EmptyState emptyState,
        List<Condition> saveViewConditions) {

    public record Tile(OrderAttention kind, String label, long count, String valueOf, String hint, String href,
                       boolean pressed, boolean enabled, String tone) {
    }

    public record Segment(String status, String label, long count, String href, boolean current) {
    }

    public record FilterOption(String id, String label, boolean shared, boolean starred, boolean selected) {
    }

    public record Chip(String label, String clearHref, String clearLabel) {
    }

    public record SortHeader(String href, String ariaSort) {
    }

    public record EmptyState(String text, String actionLabel, String actionHref) {
    }

    /** A condition of the "save this view" dialog: the filter field name and the raw value it will be saved with. */
    public record Condition(String field, String value, String label) {
    }

    public boolean canSaveView() {
        return !saveViewConditions.isEmpty();
    }
}
